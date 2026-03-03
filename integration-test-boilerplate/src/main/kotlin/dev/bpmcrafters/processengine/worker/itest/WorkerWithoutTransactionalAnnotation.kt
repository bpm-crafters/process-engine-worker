package dev.bpmcrafters.processengine.worker.itest

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.camunda.community.rest.client.api.ProcessInstanceApiClient

class WorkerWithoutTransactionalAnnotation(
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
    return super.createEntity(task, name, verified, simulateRandomTechnicalError, apiCallShouldFail)
  }

  @ProcessEngineWorker(
    topic = "example.verify-entity",
  )
  override fun verifyEntity(@Variable(name = "id") id: String) {
    super.verifyEntity(id)
  }
}
