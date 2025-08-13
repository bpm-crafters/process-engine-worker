package dev.bpmcrafters.processengine.worker.itest.camunda7.external

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import java.util.*
import java.util.concurrent.TimeUnit.SECONDS

@Import(WorkerWithTransactionalAnnotation::class)
@TestPropertySource(properties = [
  "dev.bpm-crafters.process-api.worker.complete-tasks-before-commit=false"
])
class ExternalTaskCompletionAfterCommitTest : AbstractTransactionalBehaviorTest() {

  @Test
  fun `successful worker completes task after of transaction has been committed`() {
    val name = "Big or Lil' Someone ${UUID.randomUUID()}"
    val pi = startProcess(name = name, verified = true)
    assertThat(processInstanceIsRunning(pi)).isTrue()
    await().atMost(30, SECONDS).untilAsserted {
      assertThat(processInstanceIsRunning(pi)).isFalse
      val entity = findEntityByName(name)
      assertThat(entity).isNotNull
      val task = getHistoricActivityInstances(pi, "task_create_entity")[0]
      assertThat(task.endTime).isAfterOrEqualTo(entity!!.createdAt)
    }
  }

  @Test
  fun `failing worker does not complete task`() {
    val name = "Big or Lil' Someone ${UUID.randomUUID()}"
    val pi = startProcess(name = name, verified = true, simulateRandomTechnicalError = true)
    assertThat(processInstanceIsRunning(pi)).isTrue()
    await().atMost(30, SECONDS).untilAsserted {
      val task = getExternalTasks(pi)[0]
      assertThat(task.errorMessage).isEqualTo("Simulating a technical error for task ${task.id}")
    }
    assertThat(entityExistsForName(name)).isFalse
  }
}
