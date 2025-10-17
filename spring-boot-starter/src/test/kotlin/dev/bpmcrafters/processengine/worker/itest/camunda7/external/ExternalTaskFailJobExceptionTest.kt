package dev.bpmcrafters.processengine.worker.itest.camunda7.external

import dev.bpmcrafters.processengine.worker.FailJobException
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import dev.bpmcrafters.processengine.worker.itest.camunda7.external.application.AbstractExampleProcessWorker
import dev.bpmcrafters.processengine.worker.itest.camunda7.external.application.MyEntityService
import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.camunda.community.rest.client.api.ProcessInstanceApiClient
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit.SECONDS

@Import(ExternalTaskFailJobExceptionTest.WorkerWithFailJobException::class)
@TestPropertySource(properties = [
  "dev.bpm-crafters.process-api.worker.complete-tasks-before-commit=true"
])
class ExternalTaskFailJobExceptionTest : AbstractTransactionalBehaviorTest() {

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
    assertThat(processInstanceIsRunning(pi)).isTrue()
    await().atMost(30, SECONDS).untilAsserted {
      val task = getExternalTasks(pi)[0]
      assertThat(task.errorMessage).isEqualTo("Simulating a technical error for task ${task.id}")
      assertThat(task.retries).isEqualTo(3)
    }
    assertThat(entityExistsForName(name)).isFalse
  }

}
