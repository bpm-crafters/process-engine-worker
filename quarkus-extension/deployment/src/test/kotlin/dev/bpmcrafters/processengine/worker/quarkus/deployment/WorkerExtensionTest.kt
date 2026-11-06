package dev.bpmcrafters.processengine.worker.quarkus.deployment

import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred
import dev.bpmcrafters.processengine.worker.FailJobException
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.quarkus.JtaInMemoryIdempotencyRegistry
import dev.bpmcrafters.processengine.worker.registrar.ProcessEngineWorkerMetrics
import dev.bpmcrafters.processengine.worker.registrar.VariableConverter
import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.task.*
import io.quarkus.test.QuarkusExtensionTest
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.RequestScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.interceptor.AroundInvoke
import jakarta.interceptor.Interceptor
import jakarta.interceptor.InterceptorBinding
import jakarta.interceptor.InvocationContext
import jakarta.transaction.*
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.jboss.shrinkwrap.api.ShrinkWrap
import org.jboss.shrinkwrap.api.spec.JavaArchive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class WorkerExtensionTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest().setArchiveProducer {
      ShrinkWrap.create(JavaArchive::class.java)
        .addClasses(
          Engine::class.java, Workers::class.java, GenericWorker::class.java, GenericImplementation::class.java,
          Payload::class.java, CountingMetrics::class.java, RegistryProducer::class.java, Counted::class.java,
          CountingInterceptor::class.java, RequestData::class.java, UpperCaseConverter::class.java, TestSubscription::class.java, TestMetadata::class.java,
          Outcome::class.java
        )
    }

    fun task(id: String) = TaskInformation(id, mapOf("processInstanceId" to "process"))
  }

  @Inject
  lateinit var engine: Engine
  @Inject
  lateinit var metrics: CountingMetrics
  @Inject
  lateinit var transactions: TransactionManager
  @Inject
  lateinit var registry: IdempotencyRegistry

  @Test
  fun discoversUnusedBeansInvokesProxyAndActivatesRequestContextOnDeliveryThread() {
    val task = task("generic-task")
    CompletableFuture.runAsync { engine.deliver("generic", task, mapOf("value" to mapOf("name" to "Alice"))) }.join()
    assertThat(engine.results[task.taskId]).containsEntry("name", "Alice").containsEntry("intercepted", true)
    assertThat(metrics.received).isPositive()
    assertThat(engine.subscriptions.keys).contains("generic", "after", "before", "rollback", "new", "manual", "supports", "never", "not-supported")
  }

  @Test
  fun preservesAnAlreadyActiveRequestContext() {
    val context = io.quarkus.arc.Arc.container().requestContext()
    context.activate()
    try {
      engine.deliver("generic", task("active-request-task"), mapOf("value" to mapOf("name" to "Alice")))
      assertThat(context.isActive).isTrue()
      assertThat(engine.results["active-request-task"]).containsEntry("intercepted", true)
    } finally {
      context.terminate()
    }
  }

  @Test
  fun completesAfterOwnedCommitAndBeforeCommitWhenRequested() {
    engine.deliver("after", task("after-task"), emptyMap())
    assertThat(engine.completionStatuses["after-task"]).isEqualTo(Status.STATUS_NO_TRANSACTION)
    assertThat(engine.events).contains("after:committed")
    engine.deliver("before", task("before-task"), emptyMap())
    assertThat(engine.completionStatuses["before-task"]).isEqualTo(Status.STATUS_ACTIVE)
    assertThat(registry.getTaskResult(task("before-task"))).isNull()
  }

  @Test
  fun rollsBackWithoutPublishingResultOrCompletingTask() {
    engine.deliver("rollback", task("rollback-task"), emptyMap())
    assertThat(engine.results).doesNotContainKey("rollback-task")
    assertThat(engine.failures).containsKey("rollback-task")
    assertThat(registry.getTaskResult(task("rollback-task"))).isNull()
    assertThat(engine.events).contains("rollback:rolled-back")
  }

  @Test
  fun preservesBusinessErrorAndExplicitRetryMetadataThroughCdiAndReflection() {
    engine.deliver("business-error", task("business-error-task"), emptyMap())
    assertThat(engine.errors["business-error-task"]!!.errorCode).isEqualTo("business-code")
    engine.deliver("explicit-failure", task("explicit-failure-task"), emptyMap())
    assertThat(engine.failures["explicit-failure-task"]!!.retryCount).isEqualTo(4)
    assertThat(engine.failures["explicit-failure-task"]!!.retryBackoff).isEqualTo(Duration.ofSeconds(30))
  }

  @Test
  fun discoversCustomNoArgConverterMetadata() {
    engine.deliver("custom-converter", task("custom-converter-task"), mapOf("value" to "hello"))
    assertThat(engine.results["custom-converter-task"]).containsEntry("value", "HELLO")
  }

  @Test
  fun requiresNewIsCdiOwnedAndNotNestedInsideAnotherWorkerTransaction() {
    engine.deliver("new", task("new-task"), emptyMap())
    assertThat(engine.events).contains("new:committed")
    assertThat(engine.completionStatuses["new-task"]).isEqualTo(Status.STATUS_NO_TRANSACTION)
    assertThat(engine.interceptorStatuses["newTransaction"]).isEqualTo(Status.STATUS_NO_TRANSACTION)
  }

  @Test
  fun failedAfterCommitCompletionCachesResultForRetryWithoutReinvokingWorker() {
    val task = task("retry-task")
    engine.failingCompletions.add(task.taskId)
    engine.deliver("retry", task, emptyMap())
    assertThat(registry.getTaskResult(task)).containsEntry("retry-result", true)
    engine.failingCompletions.remove(task.taskId)
    engine.deliver("retry", task, emptyMap())
    assertThat(engine.events.filter { it == "retry:${task.taskId}:invoked" }).hasSize(1)
    assertThat(engine.results[task.taskId]).containsEntry("retry-result", true)
    assertThat(registry.getTaskResult(task)).isNull()
  }

  @Test
  fun failedBeforeCommitCompletionRollsBackAndNeverPublishesCachedResult() {
    val task = task("before-failure-task")
    engine.failingCompletions.add(task.taskId)
    engine.deliver("before", task, emptyMap())
    assertThat(registry.getTaskResult(task)).isNull()
    assertThat(engine.failures).containsKey(task.taskId)
    assertThat(engine.events).contains("before:rolled-back")
  }

  @Test
  fun nonOwningModesAndManualCompletionPreserveCdiSemantics() {
    for (topic in listOf("supports", "never", "not-supported")) {
      engine.deliver(topic, task(topic), emptyMap())
      assertThat(engine.completionStatuses[topic]).isEqualTo(Status.STATUS_NO_TRANSACTION)
    }
    engine.deliver("manual", task("manual-task"), emptyMap())
    assertThat(engine.completionStatuses["manual-task"]).isEqualTo(Status.STATUS_ACTIVE)
    assertThat(engine.completionCounts["manual-task"]).isEqualTo(1)
    assertThat(registry.getTaskResult(task("manual-task"))).isEmpty()
  }

  @Test
  fun defersAmbientCompletionWithoutOwningTheCallersTransaction() {
    transactions.begin()
    try {
      val ambient = transactions.transaction
      engine.deliver("after", task("ambient-task"), emptyMap())
      assertThat(engine.results).doesNotContainKey("ambient-task")
      assertThat(engine.failures).doesNotContainKey("ambient-task")
      assertThat(transactions.transaction).isSameAs(ambient)
      assertThat(ambient.status).isEqualTo(Status.STATUS_ACTIVE)
      transactions.commit()
      engine.completion("ambient-task").get(5, TimeUnit.SECONDS)
      assertThat(engine.results).containsKey("ambient-task")
    } finally {
      if (transactions.transaction != null) transactions.rollback()
    }
  }

  @Test
  fun cachedRedeliveryAlsoDefersCompletionInAnAmbientTransaction() {
    val task = task("ambient-cache-task")
    engine.failingCompletions.add(task.taskId)
    engine.deliver("retry", task, emptyMap())
    assertThat(registry.getTaskResult(task)).containsEntry("retry-result", true)
    engine.failingCompletions.remove(task.taskId)
    transactions.begin()
    try {
      val ambient = transactions.transaction
      engine.deliver("retry", task, emptyMap())
      assertThat(engine.results).doesNotContainKey(task.taskId)
      assertThat(transactions.transaction).isSameAs(ambient)
      transactions.commit()
      engine.completion(task.taskId).get(5, TimeUnit.SECONDS)
      assertThat(engine.events.filter { it == "retry:${task.taskId}:invoked" }).hasSize(1)
      await().atMost(Duration.ofSeconds(5)).untilAsserted { assertThat(registry.getTaskResult(task)).isNull() }
    } finally {
      if (transactions.transaction != null) transactions.rollback()
    }
  }

  @Singleton
  open class Engine : TaskSubscriptionApi, ServiceTaskCompletionApi {
    @Inject
    lateinit var transactions: TransactionManager
    val subscriptions = ConcurrentHashMap<String, SubscribeForTaskCmd>()
    val results = ConcurrentHashMap<String, Map<String, Any?>>()
    val completionStatuses = ConcurrentHashMap<String, Int>()
    val completionCounts = ConcurrentHashMap<String, Int>()
    val interceptorStatuses = ConcurrentHashMap<String, Int>()
    val failingCompletions: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val failures = ConcurrentHashMap<String, FailTaskCmd>()
    val errors = ConcurrentHashMap<String, CompleteTaskByErrorCmd>()
    val events = CopyOnWriteArrayList<String>()
    val completed = ConcurrentHashMap<String, CompletableFuture<Void>>()

    fun completion(taskId: String): CompletableFuture<Void> = completed.computeIfAbsent(taskId) { CompletableFuture() }
    fun deliver(topic: String, task: TaskInformation, payload: Map<String, Any?>) {
      subscriptions[topic]!!.action.accept(task, payload)
    }

    override fun subscribeForTask(command: SubscribeForTaskCmd): CompletableFuture<TaskSubscription> {
      subscriptions[requireNotNull(command.taskDescriptionKey)] = command
      return CompletableFuture.completedFuture(TestSubscription())
    }

    override fun unsubscribe(command: UnsubscribeFromTaskCmd): CompletableFuture<Empty> = CompletableFuture.completedFuture(Empty)
    override fun getSupportedRestrictions(): Set<String> = emptySet()
    override fun meta(instance: MetaInfoAware): MetaInfo = TestMetadata()
    override fun completeTask(command: CompleteTaskCmd): CompletableFuture<Empty> = try {
      completionStatuses[command.taskId] = transactions.status
      if (command.taskId in failingCompletions) CompletableFuture.failedFuture(IllegalStateException("completion failed"))
      else {
        completionCounts.merge(command.taskId, 1) { first, second -> first + second }
        results[command.taskId] = command.get()
        completion(command.taskId).complete(null)
        CompletableFuture.completedFuture(Empty)
      }
    } catch (failure: Exception) {
      CompletableFuture.failedFuture(failure)
    }

    override fun completeTaskByError(command: CompleteTaskByErrorCmd): CompletableFuture<Empty> {
      errors[command.taskId] = command
      return CompletableFuture.completedFuture(Empty)
    }

    override fun failTask(command: FailTaskCmd): CompletableFuture<Empty> {
      failures[command.taskId] = command
      return CompletableFuture.completedFuture(Empty)
    }
  }

  class TestSubscription : TaskSubscription
  class TestMetadata : MetaInfo

  @ApplicationScoped
  @Transactional
  class Workers {
    @Inject
    lateinit var engine: Engine
    @Inject
    lateinit var transactions: TransactionManager
    @ProcessEngineWorker(topic = "after")
    fun after() {
      enlist("after")
    }

    @ProcessEngineWorker(topic = "before", completion = ProcessEngineWorker.Completion.BEFORE_COMMIT)
    fun before(): Map<String, Any?> {
      enlist("before")
      return mapOf("value" to true)
    }

    @ProcessEngineWorker(topic = "retry")
    fun retry(task: TaskInformation): Map<String, Any?> {
      engine.events.add("retry:${task.taskId}:invoked")
      return mapOf("retry-result" to true)
    }

    @ProcessEngineWorker(topic = "rollback")
    fun rollback() {
      enlist("rollback")
      throw IllegalArgumentException("worker failure")
    }

    @ProcessEngineWorker(topic = "business-error")
    @Throws(BpmnErrorOccurred::class)
    fun businessError() {
      throw BpmnErrorOccurred("business error", "business-code", mapOf("value" to 1))
    }

    @ProcessEngineWorker(topic = "explicit-failure")
    @Throws(FailJobException::class)
    fun explicitFailure() {
      throw FailJobException("explicit failure", null, 4, Duration.ofSeconds(30))
    }

    @ProcessEngineWorker(topic = "custom-converter")
    fun customConverter(@Variable(value = "value", converter = UpperCaseConverter::class) value: String): Map<String, Any?> = mapOf("value" to value)

    @ProcessEngineWorker(topic = "new")
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    @Counted
    fun newTransaction() {
      enlist("new")
    }

    @ProcessEngineWorker(topic = "supports")
    @Transactional(Transactional.TxType.SUPPORTS)
    fun supports() {
    }

    @ProcessEngineWorker(topic = "never")
    @Transactional(Transactional.TxType.NEVER)
    fun never() {
    }

    @ProcessEngineWorker(topic = "not-supported")
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    fun notSupported() {
    }

    @ProcessEngineWorker(topic = "manual", autoComplete = false)
    fun manual(task: TaskInformation, api: ServiceTaskCompletionApi) {
      api.completeTask(CompleteTaskCmd(task.taskId) { emptyMap() }).join()
    }

    fun enlist(topic: String) {
      try {
        transactions.transaction.registerSynchronization(Outcome(engine, topic))
      } catch (failure: Exception) {
        throw IllegalStateException(failure)
      }
    }
  }

  class Outcome(private val engine: Engine, private val topic: String) : Synchronization {
    override fun beforeCompletion() {}
    override fun afterCompletion(status: Int) {
      engine.events.add(topic + if (status == Status.STATUS_COMMITTED) ":committed" else ":rolled-back")
    }
  }

  interface GenericWorker<T> {
    @ProcessEngineWorker(topic = "generic")
    fun work(@Variable("value") value: T): Map<String, Any?>
  }

  @ApplicationScoped
  class GenericImplementation : GenericWorker<Payload> {
    @Inject
    lateinit var request: RequestData
    @Counted
    override fun work(value: Payload): Map<String, Any?> = mapOf("name" to value.name, "intercepted" to request.intercepted())
  }

  data class Payload(val name: String)
  class UpperCaseConverter : VariableConverter {
    override fun <T : Any> mapToType(value: Any?, type: Class<T>): T = type.cast((value as String).uppercase(Locale.ROOT))
  }

  @RequestScoped
  class RequestData {
    private var intercepted = false
    fun intercepted() = intercepted
    fun mark() {
      intercepted = true
    }
  }

  @InterceptorBinding
  @Retention(AnnotationRetention.RUNTIME)
  @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
  annotation class Counted

  @Interceptor
  @Counted
  @Priority(100)
  class CountingInterceptor {
    @Inject
    lateinit var request: RequestData
    @Inject
    lateinit var engine: Engine
    @Inject
    lateinit var transactions: TransactionManager
    @AroundInvoke
    @Throws(Exception::class)
    fun invoke(context: InvocationContext): Any? {
      engine.interceptorStatuses[context.method.name] = transactions.status
      request.mark()
      return context.proceed()
    }
  }

  @Singleton
  class CountingMetrics : ProcessEngineWorkerMetrics {
    var received = 0
    override fun taskReceived(topic: String) {
      received++
    }

    override fun taskCompleted(topic: String) {}
    override fun taskCompletedByError(topic: String) {}
    override fun taskFailed(topic: String) {}
  }

  @Singleton
  class RegistryProducer {
    @Produces
    @Singleton
    fun registry(transactions: TransactionSynchronizationRegistry): IdempotencyRegistry = JtaInMemoryIdempotencyRegistry(transactions)
  }
}
