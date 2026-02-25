package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerProperties
import dev.bpmcrafters.processengine.worker.idempotency.InMemoryIdempotencyRegistry
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.util.*
import java.util.concurrent.CompletableFuture

class ProcessEngineIdempotencyTest {

  private val properties = ProcessEngineWorkerProperties(enableIdempotencyRegistry = true)
  private val taskSubscriptionApi = mock<TaskSubscriptionApi>()
  private val taskCompletionApi = mock<ServiceTaskCompletionApi> {
    on { completeTask(any()) } doReturn CompletableFuture.completedFuture(null)
  }
  private val variableConverter = mock<VariableConverter>()
  private val parameterResolver = ParameterResolver.builder().build()
  private val resultResolver = ResultResolver.builder().build()
  private val transactionalTemplate = mock<TransactionTemplate> {
    on { execute(any<TransactionCallback<Any>>()) } doAnswer { invocation ->
      val callback = invocation.getArgument<TransactionCallback<Any>>(0)
      callback.doInTransaction(mock())
    }
  }
  private val metrics = mock<ProcessEngineWorkerMetrics>()
  private val idempotencyRegistry = InMemoryIdempotencyRegistry(enableIdempotencyRegistry = true)

  private val registrar = ProcessEngineStarterRegistrar(
    properties,
    taskSubscriptionApi,
    taskCompletionApi,
    variableConverter,
    parameterResolver,
    resultResolver,
    transactionalTemplate,
    metrics,
    idempotencyRegistry
  )

  @Test
  fun `should not invoke annotated method again for same task`() {
    // Given a worker method and a counting action
    var invocationCount = 0
    val taskId = UUID.randomUUID().toString()
    val taskInfo = TaskInformation(taskId = taskId, meta = mapOf())
    val payload = mapOf<String, Any?>()

    val actionWithResult = ProcessEngineStarterRegistrar.TaskHandlerWithResult { _, _ ->
      invocationCount++
      "result-$invocationCount"
    }

    // Access private method workerAndApiInvocation via reflection to exercise idempotency logic
    val method = ProcessEngineStarterRegistrar::class.java.getDeclaredMethod(
      "workerAndApiInvocation",
      TaskInformation::class.java,
      Map::class.java,
      ProcessEngineStarterRegistrar.TaskHandlerWithResult::class.java
    )
    method.isAccessible = true

    // When - first call processes and stores result
    val result1 = method.invoke(registrar, taskInfo, payload, actionWithResult) as String?

    // Then - worker invoked once
    assertThat(invocationCount).isEqualTo(1)
    assertThat(result1).isEqualTo("result-1")

    // When - second call with same TaskInformation
    val result2 = method.invoke(registrar, taskInfo, payload, actionWithResult) as String?

    // Then - worker not invoked again, cached result returned
    assertThat(invocationCount).isEqualTo(1)
    assertThat(result2).isEqualTo("result-1")
  }
}
