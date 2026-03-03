package dev.bpmcrafters.processengine.worker.idempotency

import dev.bpmcrafters.processengine.worker.itest.AbstractBehaviorIT
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import org.assertj.core.api.Assertions
import org.awaitility.Awaitility
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doCallRealMethod
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.util.UUID
import java.util.concurrent.TimeUnit

@TestPropertySource(properties = [
  "dev.bpm-crafters.process-api.worker.complete-tasks-before-commit=false"
])
@MockitoSpyBean(types = [IdempotencyRegistry::class])
@MockitoSpyBean(name = "c7remote-service-task-completion-api", types = [ServiceTaskCompletionApi::class])
abstract class AbstractIdempotencyIT : AbstractBehaviorIT() {

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
    Assertions.assertThat(processInstanceIsRunning(processInstanceId)).isTrue()
    Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted {
      val entity = findEntityByName(name)
      Assertions.assertThat(entity).isNotNull
    }
    doCallRealMethod().`when`(serviceTaskCompletionApi).completeTask(any())
    unlockExternalTask(getExternalTasks(processInstanceId).first().id!!)
    print(idempotencyRegistry)
    Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted {
      Assertions.assertThat(processInstanceIsRunning(processInstanceId)).isFalse
    }
    val inOrder = inOrder(idempotencyRegistry)
    inOrder.verify(idempotencyRegistry).getTaskResult(any())
    inOrder.verify(idempotencyRegistry).register(any(), any())
    inOrder.verify(idempotencyRegistry).getTaskResult(any())
    inOrder.verify(idempotencyRegistry, never()).register(any(), any())
  }

}
