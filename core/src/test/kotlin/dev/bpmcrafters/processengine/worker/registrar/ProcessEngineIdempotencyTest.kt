package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerConfiguration
import dev.bpmcrafters.processengine.worker.idempotency.InMemoryIdempotencyRegistry
import dev.bpmcrafters.processengine.worker.transaction.TransactionalExecutor
import dev.bpmcrafters.processengineapi.CommonRestrictions.PROCESS_INSTANCE_ID
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.*
import java.util.concurrent.CompletableFuture

class ProcessEngineIdempotencyTest {

  private val taskSubscriptionApi = mock<TaskSubscriptionApi>()
  private val taskCompletionApi = mock<ServiceTaskCompletionApi> {
    on { completeTask(any()) } doReturn CompletableFuture.completedFuture(null)
  }
  private val variableConverter = mock<VariableConverter>()
  private val parameterResolver = ParameterResolver.builder().build()
  private val resultResolver = ResultResolver.builder().build()
  private val metrics = mock<ProcessEngineWorkerMetrics>()
  private val idempotencyRegistry = InMemoryIdempotencyRegistry()

  private val registrar = ProcessEngineWorkerRegistrar(
    ProcessEngineWorkerConfiguration(),
    taskSubscriptionApi,
    taskCompletionApi,
    variableConverter,
    parameterResolver,
    resultResolver,
    TransactionalExecutor.NONE,
    metrics,
    idempotencyRegistry
  )

  @Test
  fun `should not invoke annotated method again for same task`() {
    // Given a worker method and a counting action
    var invocationCount = 0
    val processInstanceId = UUID.randomUUID().toString()
    val taskId = UUID.randomUUID().toString()
    val taskInfo = TaskInformation(taskId = taskId, meta = mapOf(PROCESS_INSTANCE_ID to processInstanceId))
    val payload = mapOf<String, Any?>()

    val actionWithResult = ProcessEngineWorkerRegistrar.TaskHandlerWithResult { _, _ ->
      invocationCount++
      "result-$invocationCount"
    }

    // When - first call processes and stores result
    val result1 = registrar.workerAndApiInvocation(taskInfo, payload, actionWithResult, false, Any::class.java.methods[0])

    // Then - worker invoked once
    assertThat(invocationCount).isEqualTo(1)
    assertThat(result1).isEmpty()

    // When - second call with same TaskInformation
    val result2 = registrar.workerAndApiInvocation(taskInfo, payload, actionWithResult, false, Any::class.java.methods[0])

    // Then - worker not invoked again, cached result returned
    assertThat(invocationCount).isEqualTo(1)
    assertThat(result2).isEmpty()
  }

  @Test
  fun `should install the after commit hook of the transactional executor on the registry`() {
    assertThat(idempotencyRegistry.afterCommitHook).isSameAs(TransactionalExecutor.NONE)
  }
}
