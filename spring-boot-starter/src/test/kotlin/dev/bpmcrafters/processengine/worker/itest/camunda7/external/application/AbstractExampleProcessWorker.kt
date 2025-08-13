package dev.bpmcrafters.processengine.worker.itest.camunda7.external.application

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.task.TaskInformation
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.community.rest.client.api.ProcessInstanceApiClient
import org.camunda.community.rest.client.model.SuspensionStateDto

private val logger = KotlinLogging.logger {}

abstract class AbstractExampleProcessWorker(
  protected val myEntityService: MyEntityService,
  protected val processInstanceApiClient: ProcessInstanceApiClient,
) {

  open fun createEntity(
    task: TaskInformation,
    name: String,
    verified: Boolean,
    simulateRandomTechnicalError: Boolean,
    apiCallShouldFail: Boolean
  ): Map<String, Any> {
    logger.info { "start executing worker 'example.create-entity' " }

    if (simulateRandomTechnicalError) {
      val message = "Simulating a technical error for task ${task.taskId}"
      logger.info { message }
      throw RuntimeException(message)
    }

    if (apiCallShouldFail) {
      val processInstanceId = task.meta[CommonRestrictions.PROCESS_INSTANCE_ID]!!
      logger.info { "simulating external API error by suspending the processInstance: $processInstanceId" }
      processInstanceApiClient.updateSuspensionStateById(processInstanceId, SuspensionStateDto().suspended(true))
    }

    // this fails with unique constraint when we use the name twice
    // this fails with BPMN Error when verified is false
    val entity = myEntityService.createEntity(name, verified)

    return mapOf("id" to entity.id)
  }

  open fun verifyEntity(
    id: String,
  ) {
    logger.info { "start executing worker 'example.verify-entity' " }

    myEntityService.verify(id)
  }
}
