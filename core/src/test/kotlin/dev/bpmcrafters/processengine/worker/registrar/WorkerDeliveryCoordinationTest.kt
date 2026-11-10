package dev.bpmcrafters.processengine.worker.registrar

import com.fasterxml.jackson.databind.ObjectMapper
import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred
import dev.bpmcrafters.processengine.worker.FailJobException
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import dev.bpmcrafters.processengine.worker.idempotency.LocalInMemoryIdempotencyRegistry
import dev.bpmcrafters.processengine.worker.registrar.metrics.ProcessEngineWorkerMetricsNoOp
import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.task.CompleteTaskByErrorCmd
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd
import dev.bpmcrafters.processengineapi.task.FailTaskCmd
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.SubscribeForTaskCmd
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskSubscription
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import dev.bpmcrafters.processengineapi.task.UnsubscribeFromTaskCmd
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class WorkerDeliveryCoordinationTest {
  private val engine = Engine()
  private val registry = LocalInMemoryIdempotencyRegistry()
  private val task = TaskInformation("task", mapOf("processInstanceId" to "process"))

  @Test
  fun keepsCoreResolutionCachingAndCompletionAvailableToDeferredCoordination() {
    val pending = AtomicReference<WorkerDelivery>()
    val method = Worker::class.java.getMethod("work", String::class.java)
    runtime().register(method, method, pending::set) { arguments -> mapOf<String, Any?>("output" to arguments[0]) }
    engine.command.action.accept(task, mapOf<String, Any?>("input" to "value"))
    assertThat(engine.completed).isEmpty()
    assertThat(engine.command.payloadDescription).containsExactly("input")
    val delivery = pending.get()
    assertThat(delivery.autoComplete()).isTrue()
    assertThat(delivery.completeBeforeCommit()).isTrue()
    assertThat(delivery.cachedResult()).isNull()
    val result = delivery.processResult(delivery.invoke())
    assertThat(registry.getTaskResult(task)).containsEntry("output", "value")
    delivery.complete(result)
    assertThat(engine.completed).hasSize(1)
    assertThat(engine.completed.single().get()).containsEntry("output", "value")
    assertThat(registry.getTaskResult(task)).isNull()
  }

  @Test
  fun cacheReplayDoesNotResolveMissingRequiredArgumentsOrInvokeWorker() {
    val calls = AtomicInteger()
    registry.register(task, mapOf<String, Any?>("output" to "cached"))
    val method = Worker::class.java.getMethod("work", String::class.java)
    runtime().register(method, method, { delivery -> delivery.complete(requireNotNull(delivery.cachedResult())) }) {
      calls.incrementAndGet()
      emptyMap<String, Any?>()
    }
    engine.command.action.accept(task, emptyMap())
    assertThat(calls).hasValue(0)
    assertThat(engine.completed[0].get()).containsEntry("output", "cached")
  }

  @Test
  fun supportsDeferredFailureReportingWithoutDuplicatingCoreRetryLogic() {
    val pending = AtomicReference<WorkerDelivery>()
    val method = Worker::class.java.getMethod("work", String::class.java)
    runtime().register(method, method, pending::set) { emptyMap<String, Any?>() }
    engine.command.action.accept(task, mapOf<String, Any?>("input" to "value"))
    pending.get().reportFailure(InvocationTargetException(FailJobException("retry", null, 8, Duration.ofSeconds(3))))
    assertThat(engine.failed).hasSize(1)
    val command = engine.failed.single()
    assertThat(command.retryCount).isEqualTo(8)
    assertThat(command.retryBackoff).isEqualTo(Duration.ofSeconds(3))
  }

  @Test
  fun synchronousCoordinatorFailureKeepsBusinessErrorUnwrapping() {
    val method = Worker::class.java.getMethod("work", String::class.java)
    runtime().register(method, method, {
      throw ExecutionException(InvocationTargetException(BpmnErrorOccurred("message", "code", emptyMap())))
    }) { emptyMap<String, Any?>() }
    engine.command.action.accept(task, emptyMap())
    assertThat(engine.errors).hasSize(1)
    assertThat(engine.errors.single().errorCode).isEqualTo("code")
    assertThat(engine.failed).isEmpty()
  }

  @Test
  fun completionStageWrappersPreserveCoreBusinessErrorClassification() {
    val method = Worker::class.java.getMethod("work", String::class.java)
    runtime().register(method, method, {
      throw CompletionException(InvocationTargetException(BpmnErrorOccurred("message", "stage-code", emptyMap())))
    }) { emptyMap<String, Any?>() }
    engine.command.action.accept(task, emptyMap())
    assertThat(engine.errors).hasSize(1)
    assertThat(engine.errors.single().errorCode).isEqualTo("stage-code")
  }

  @Test
  fun manualModeAndDefaultCompletionOptionsRemainVisibleWithoutUsingLegacyExecutor() {
    val pending = AtomicReference<WorkerDelivery>()
    val method = Worker::class.java.getMethod("manual")
    runtime().register(method, method, pending::set) { null }
    engine.command.action.accept(task, emptyMap())
    assertThat(pending.get().autoComplete()).isFalse()
    assertThat(pending.get().completeBeforeCommit()).isFalse()
    assertThat(pending.get().processResult(pending.get().invoke())).isEmpty()
    assertThat(engine.completed).isEmpty()
  }

  @Test
  fun coordinatedBusinessErrorReportingFailureCannotFallBackToAnotherTerminalCommand() {
    val original = BpmnErrorOccurred("business failure", "business-code", emptyMap())
    val reportingFailure = IllegalStateException("error API unavailable")
    engine.errorReportFailure = reportingFailure
    val method = Worker::class.java.getMethod("work", String::class.java)
    runtime().register(method, method, { delivery ->
      delivery.reportFailure(original)
      throw IllegalArgumentException("coordinator failed after reporting")
    }) { emptyMap<String, Any?>() }
    assertThatCode { engine.command.action.accept(task, emptyMap()) }.doesNotThrowAnyException()
    assertThat(engine.errors).hasSize(1)
    assertThat(engine.errors.single().errorCode).isEqualTo("business-code")
    assertThat(engine.failed).isEmpty()
    assertThat(original.suppressed).contains(reportingFailure)
  }

  @Test
  fun coordinatedTechnicalReportingFailureIsLoggedWithOriginalWithoutRetryingTheReporter() {
    val original = IllegalArgumentException("worker failure")
    val reportingFailure = IllegalStateException("failure API unavailable")
    engine.technicalReportFailure = reportingFailure
    val method = Worker::class.java.getMethod("work", String::class.java)
    runtime().register(method, method, { throw original }) { emptyMap<String, Any?>() }
    assertThatCode { engine.command.action.accept(task, emptyMap()) }.doesNotThrowAnyException()
    assertThat(engine.failed).hasSize(1)
    assertThat(engine.failed.single().reason).isEqualTo("worker failure")
    assertThat(engine.errors).isEmpty()
    assertThat(original.suppressed).contains(reportingFailure)
  }

  @Test
  fun legacyRegistrationStillPropagatesSynchronousReportingExceptions() {
    val original = BpmnErrorOccurred("business failure", "legacy-code", emptyMap())
    val reportingFailure = IllegalStateException("legacy reporter failure")
    engine.errorReportFailure = reportingFailure
    val method = Worker::class.java.getMethod("work", String::class.java)
    runtime().register(method, method, false) { throw original }
    assertThatThrownBy { engine.command.action.accept(task, mapOf<String, Any?>("input" to "value")) }.isSameAs(reportingFailure)
    assertThat(engine.errors).hasSize(1)
    assertThat(engine.failed).isEmpty()
  }

  private fun runtime() = ProcessEngineWorkerRegistrar(
    WorkerRuntimeOptions(), engine, engine,
    JacksonVariableConverter(ObjectMapper()), ParameterResolver.builder().build(), ResultResolver.builder().build(),
    { throw AssertionError("The additive coordinator must not invoke the legacy transaction executor") },
    ProcessEngineWorkerMetricsNoOp, registry
  )

  class Worker {
    @ProcessEngineWorker(completion = ProcessEngineWorker.Completion.BEFORE_COMMIT)
    fun work(@Variable(name = "input") value: String): Map<String, Any?> = mapOf("output" to value)

    @ProcessEngineWorker(autoComplete = false)
    fun manual() {}
  }

  private class Engine : TaskSubscriptionApi, ServiceTaskCompletionApi {
    lateinit var command: SubscribeForTaskCmd
    val completed = mutableListOf<CompleteTaskCmd>()
    val failed = mutableListOf<FailTaskCmd>()
    val errors = mutableListOf<CompleteTaskByErrorCmd>()
    var errorReportFailure: RuntimeException? = null
    var technicalReportFailure: RuntimeException? = null

    override fun subscribeForTask(cmd: SubscribeForTaskCmd): CompletableFuture<TaskSubscription> {
      command = cmd
      return CompletableFuture.completedFuture(object : TaskSubscription {})
    }

    override fun unsubscribe(cmd: UnsubscribeFromTaskCmd): CompletableFuture<Empty> = done()
    override fun getSupportedRestrictions(): Set<String> = emptySet()
    override fun meta(instance: MetaInfoAware): MetaInfo = object : MetaInfo {}

    override fun completeTask(cmd: CompleteTaskCmd): CompletableFuture<Empty> {
      completed.add(cmd)
      return done()
    }

    override fun completeTaskByError(cmd: CompleteTaskByErrorCmd): CompletableFuture<Empty> {
      errors.add(cmd)
      errorReportFailure?.let { throw it }
      return done()
    }

    override fun failTask(cmd: FailTaskCmd): CompletableFuture<Empty> {
      failed.add(cmd)
      technicalReportFailure?.let { throw it }
      return done()
    }

    private fun done(): CompletableFuture<Empty> = CompletableFuture.completedFuture(Empty)
  }
}
