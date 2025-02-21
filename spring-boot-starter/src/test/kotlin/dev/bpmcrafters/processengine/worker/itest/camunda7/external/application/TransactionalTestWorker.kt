package dev.bpmcrafters.processengine.worker.itest.camunda7.external.application

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class TransactionalTestWorker(
  private val myEntityService: MyEntityService
) {

  @ProcessEngineWorker(
    topic = "example.create-entity"
  )
  fun createEntity(
    @Variable(name = "name") name: String,
    @Variable(name = "verified") verified: Boolean
  ): Map<String, Any> {
    logger.info { "start executing worker 'example.create-entity' " }
    val entity = myEntityService.createEntity(name, verified)

    return mapOf("id" to entity.id)
  }

  @ProcessEngineWorker(
    topic = "example.verify-entity",
  )
  fun verifyEntity(
    @Variable(name = "id") id: String,
  ) {
    logger.info { "start executing worker 'example.verify-entity' " }

    myEntityService.verify(id)
  }
}
