package dev.bpmcrafters.example.camunda7remote.worker

import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription
import org.camunda.bpm.client.task.ExternalTask
import org.camunda.bpm.client.task.ExternalTaskHandler
import org.camunda.bpm.client.task.ExternalTaskService
import org.hibernate.query.results.Builders.entity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.lang.Thread.sleep

private val logger = KotlinLogging.logger {}

@Component
@ExternalTaskSubscription(
  topicName = "example.create-entity",
  variableNames = ["name","verified"],
  lockDuration = 60_000
)
@Transactional
class CreateEntityWorker(
  private val myEntityService: MyEntityService,
) : ExternalTaskHandler {

  override fun execute(externalTask: ExternalTask, externalTaskService: ExternalTaskService) = try {
    val name = externalTask.getVariable<String>("name")!!
    val verified = externalTask.getVariable<Boolean>("verified")!!

    val entity = myEntityService.createEntity(name, verified)

//    logger.info { "taking a nap ... cancel the processInstance NOW!" }
//    sleep(30_000)

    externalTaskService.complete(externalTask, mapOf("id" to entity.id))
  } catch (e: EntityNotVerified) {
    externalTaskService.handleBpmnError(externalTask, "ERROR",e.message, mapOf("id" to e.entity.id))
  } catch (e: RuntimeException) {
    externalTaskService.handleFailure(
      externalTask,
      e.message,
      e.stackTraceToString(),
      externalTask.retries?.let { it - 1 } ?: 3,
      1_000
    )
  }
}
