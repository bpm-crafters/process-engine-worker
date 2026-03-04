package dev.bpmcrafters.processengine.worker.fixture.worker

import dev.bpmcrafters.processengine.worker.FailJobException
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import dev.bpmcrafters.processengine.worker.fixture.MyEntityService
import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.camunda.community.rest.client.api.ProcessInstanceApiClient
import java.time.Duration

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
