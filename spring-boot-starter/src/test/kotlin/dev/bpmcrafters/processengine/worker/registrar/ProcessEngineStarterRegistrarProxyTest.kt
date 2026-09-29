package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerProperties
import dev.bpmcrafters.processengine.worker.idempotency.NoOpIdempotencyRegistry
import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.SubscribeForTaskCmd
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import org.aopalliance.intercept.MethodInterceptor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.aop.framework.ProxyFactory
import org.springframework.transaction.support.TransactionTemplate
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * Verifies that workers are detected on the target class of an AOP proxy and invoked through the proxy (so that
 * interceptors like the transactional interceptor apply).
 */
internal class ProcessEngineStarterRegistrarProxyTest {

  open class Worker {
    var invocations = 0

    @ProcessEngineWorker("proxied")
    open fun work(): Map<String, Any> {
      invocations++
      return mapOf("done" to true)
    }
  }

  private val taskSubscriptionApi = mock<TaskSubscriptionApi> {
    on { subscribeForTask(any()) } doReturn CompletableFuture.completedFuture(mock())
  }
  private val taskCompletionApi = mock<ServiceTaskCompletionApi> {
    on { completeTask(any()) } doReturn CompletableFuture.completedFuture(Empty)
  }
  private val idempotencyRegistry = NoOpIdempotencyRegistry()

  private val registrar = ProcessEngineStarterRegistrar(
    ProcessEngineWorkerProperties(),
    taskSubscriptionApi,
    taskCompletionApi,
    mock(),
    ParameterResolver.builder().build(),
    ResultResolver.builder().build(),
    mock<TransactionTemplate>(),
    mock(),
    idempotencyRegistry
  )

  @Test
  fun `detects workers on the target class and invokes through the proxy`() {
    val target = Worker()
    var intercepted = 0
    val proxy = ProxyFactory(target).apply {
      isProxyTargetClass = true
      addAdvice(MethodInterceptor { invocation ->
        intercepted++
        invocation.proceed()
      })
    }.proxy as Worker

    assertThat(registrar.postProcessAfterInitialization(proxy, "worker")).isSameAs(proxy)

    val captor = argumentCaptor<SubscribeForTaskCmd>()
    verify(taskSubscriptionApi).subscribeForTask(captor.capture())
    assertThat(captor.firstValue.taskDescriptionKey).isEqualTo("proxied")

    captor.firstValue.action.accept(TaskInformation(UUID.randomUUID().toString(), mapOf()), mapOf())

    assertThat(target.invocations).isEqualTo(1)
    assertThat(intercepted).isEqualTo(1)
    verify(taskCompletionApi).completeTask(any())
  }

  @Test
  fun `ignores beans without workers without touching lazy collaborators`() {
    val bean = Any()
    assertThat(registrar.postProcessAfterInitialization(bean, "plain")).isSameAs(bean)
    verifyNoInteractions(taskSubscriptionApi)
  }
}
