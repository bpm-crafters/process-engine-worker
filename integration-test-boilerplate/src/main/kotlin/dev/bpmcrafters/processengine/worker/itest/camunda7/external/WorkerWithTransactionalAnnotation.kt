package dev.bpmcrafters.processengine.worker.itest.camunda7.external

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import dev.bpmcrafters.processengine.worker.itest.camunda7.external.application.AbstractExampleProcessWorker
import dev.bpmcrafters.processengine.worker.itest.camunda7.external.application.MyEntityService
import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.camunda.community.rest.client.api.ProcessInstanceApiClient
import org.springframework.transaction.annotation.Transactional

open class WorkerWithTransactionalAnnotation(
    myEntityService: MyEntityService,
    processInstanceApiClient: ProcessInstanceApiClient,
) : AbstractExampleProcessWorker(myEntityService = myEntityService, processInstanceApiClient = processInstanceApiClient) {

  @Transactional
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

  @Transactional
  @ProcessEngineWorker(
    topic = "example.verify-entity",
  )
  override fun verifyEntity(@Variable(name = "id") id: String) {
    super.verifyEntity(id)
  }
}
