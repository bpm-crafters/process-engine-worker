package dev.bpmcrafters.example.camunda7remote.worker

import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription
import org.camunda.bpm.client.task.ExternalTask
import org.camunda.bpm.client.task.ExternalTaskHandler
import org.camunda.bpm.client.task.ExternalTaskService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Component
@ExternalTaskSubscription(
  topicName = "example.verify-entity",
  variableNames = ["id"],
  //lockDuration = 60_000
)
@Transactional
class VerifyEntityWorker(
  private val myEntityService: MyEntityService,
) : ExternalTaskHandler {

  override fun execute(externalTask: ExternalTask, externalTaskService: ExternalTaskService) = try {
    val id = externalTask.getVariable<String>("id")!!

    val entity = myEntityService.verify(id)

    externalTaskService.complete(externalTask)
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
