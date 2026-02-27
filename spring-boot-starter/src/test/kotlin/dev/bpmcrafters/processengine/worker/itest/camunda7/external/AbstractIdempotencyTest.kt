package dev.bpmcrafters.processengine.worker.itest.camunda7.external

import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.util.*
import java.util.concurrent.TimeUnit.SECONDS

@Import(InMemoryIdempotencyRegistryConfiguration::class)
@TestPropertySource(properties = [
  "dev.bpm-crafters.process-api.worker.complete-tasks-before-commit=false"
])
@MockitoSpyBean(types = [IdempotencyRegistry::class])
@MockitoSpyBean(name = "c7remote-service-task-completion-api", types = [ServiceTaskCompletionApi::class])
abstract class AbstractIdempotencyTest : AbstractBehaviorTest() {

  @Autowired
  private lateinit var idempotencyRegistry: IdempotencyRegistry

  @Autowired
  private lateinit var serviceTaskCompletionApi: ServiceTaskCompletionApi

  @Test
  fun `fetching the same task does not execute business logic again`() {
    val name = "Big or Lil' Someone ${UUID.randomUUID()}"
    doThrow(IllegalStateException("Many things have gone wrong while completing a task"))
      .`when`(serviceTaskCompletionApi)
      .completeTask(any())
    val processInstanceId = startProcess(name, verified = true)
    assertThat(processInstanceIsRunning(processInstanceId)).isTrue()
    await().atMost(30, SECONDS).untilAsserted {
      val entity = findEntityByName(name)
      assertThat(entity).isNotNull
    }
    doCallRealMethod().`when`(serviceTaskCompletionApi).completeTask(any())
    unlockExternalTask(getExternalTasks(processInstanceId).first().id!!)
    await().atMost(30, SECONDS).untilAsserted {
      assertThat(processInstanceIsRunning(processInstanceId)).isFalse
    }
    val inOrder = inOrder(idempotencyRegistry)
    inOrder.verify(idempotencyRegistry).getTaskResult(any())
    inOrder.verify(idempotencyRegistry).register(any(), any())
    inOrder.verify(idempotencyRegistry).getTaskResult(any())
    inOrder.verify(idempotencyRegistry, never()).register(any(), any())
  }

}
