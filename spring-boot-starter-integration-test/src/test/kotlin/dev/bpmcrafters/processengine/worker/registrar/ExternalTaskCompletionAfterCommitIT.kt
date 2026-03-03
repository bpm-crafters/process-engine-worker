package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.itest.AbstractBehaviorIT
import dev.bpmcrafters.processengine.worker.itest.WorkerWithTransactionalAnnotation
import org.assertj.core.api.Assertions
import org.awaitility.Awaitility
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import java.util.UUID
import java.util.concurrent.TimeUnit

@Import(WorkerWithTransactionalAnnotation::class)
@TestPropertySource(properties = [
  "dev.bpm-crafters.process-api.worker.complete-tasks-before-commit=false"
])
class ExternalTaskCompletionAfterCommitIT : AbstractBehaviorIT() {

  @Test
  fun `successful worker completes task after transaction has been committed`() {
    val name = "Big or Lil' Someone ${UUID.randomUUID()}"
    val pi = startProcess(name = name, verified = true)
    Assertions.assertThat(processInstanceIsRunning(pi)).isTrue()
    Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted {
      Assertions.assertThat(processInstanceIsRunning(pi)).isFalse
      val entity = findEntityByName(name)
      Assertions.assertThat(entity).isNotNull
      val task = getHistoricActivityInstances(pi, "task_create_entity")[0]
      Assertions.assertThat(task.endTime).isAfterOrEqualTo(entity!!.createdAt)
    }
  }

  @Test
  fun `failing worker does not complete task`() {
    val name = "Big or Lil' Someone ${UUID.randomUUID()}"
    val pi = startProcess(name = name, verified = true, simulateRandomTechnicalError = true)
    Assertions.assertThat(processInstanceIsRunning(pi)).isTrue()
    Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted {
      val task = getExternalTasks(pi)[0]
      Assertions.assertThat(task.errorMessage).isEqualTo("Simulating a technical error for task ${task.id}")
    }
    Assertions.assertThat(entityExistsForName(name)).isFalse
  }
}
