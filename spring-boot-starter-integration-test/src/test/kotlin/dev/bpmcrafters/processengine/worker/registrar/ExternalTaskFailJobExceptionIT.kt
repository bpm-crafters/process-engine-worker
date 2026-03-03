package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.FailJobException
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import dev.bpmcrafters.processengine.worker.itest.AbstractBehaviorIT
import dev.bpmcrafters.processengine.worker.itest.AbstractExampleProcessWorker
import dev.bpmcrafters.processengine.worker.itest.MyEntityService
import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.assertj.core.api.Assertions
import org.awaitility.Awaitility
import org.camunda.community.rest.client.api.ProcessInstanceApiClient
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

@Import(ExternalTaskFailJobExceptionIT.WorkerWithFailJobException::class)
@TestPropertySource(properties = [
  "dev.bpm-crafters.process-api.worker.complete-tasks-before-commit=true"
])
class ExternalTaskFailJobExceptionIT : AbstractBehaviorIT() {

  class WorkerWithFailJobException(
    myEntityService: MyEntityService,
    processInstanceApiClient: ProcessInstanceApiClient,
  ) : AbstractExampleProcessWorker(
    myEntityService = myEntityService,
    processInstanceApiClient = processInstanceApiClient
  ) {
    @ProcessEngineWorker(
      topic = "example.create-entity"
    )
    override fun createEntity(
      task: TaskInformation,
      @Variable(name = "name") name: String,
      @Variable(name = "verified") verified: Boolean,
      @Variable(name = "simulateRandomTechnicalError") simulateRandomTechnicalError: Boolean,
      @Variable(name = "apiCallShouldFail") apiCallShouldFail: Boolean
    ): Map<String, Any> {
      if (simulateRandomTechnicalError) {
        val message = "Simulating a technical error for task ${task.taskId}"
        throw FailJobException(message = message, retryCount = 3, retryBackoff = Duration.ofSeconds(10))
      }
      return super.createEntity(task, name, verified, false, apiCallShouldFail)
    }

    @ProcessEngineWorker(
      topic = "example.verify-entity",
    )
    override fun verifyEntity(@Variable(name = "id") id: String) {
      super.verifyEntity(id)
    }
}


  @Test
  fun `fail job exception will fail job with specified retries`() {
    val name = "Big or Lil' Someone ${UUID.randomUUID()}"
    val pi = startProcess(name = name, verified = true, simulateRandomTechnicalError = true)
    Assertions.assertThat(processInstanceIsRunning(pi)).isTrue()
    Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted {
      val task = getExternalTasks(pi)[0]
      Assertions.assertThat(task.errorMessage).isEqualTo("Simulating a technical error for task ${task.id}")
      Assertions.assertThat(task.retries!!).isEqualTo(3)
    }
    Assertions.assertThat(entityExistsForName(name)).isFalse
  }

}
