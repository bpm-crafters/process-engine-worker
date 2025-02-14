package dev.bpmcrafters.example.camunda7remote.worker

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription
import org.camunda.bpm.client.task.ExternalTask
import org.camunda.bpm.client.task.ExternalTaskHandler
import org.camunda.bpm.client.task.ExternalTaskService
import org.springframework.stereotype.Component

@Component
@ExternalTaskSubscription(
  topicName = "example.create-entity",
  variableNames = ["name"],
)
class MyWorker(
  private val myEntityService: MyEntityService,
) : ExternalTaskHandler {
  override fun execute(externalTask: ExternalTask, externalTaskService: ExternalTaskService) = try {
    val name = externalTask.getVariable<String>("name")!!
    val entity = myEntityService.createEntity(name)

    externalTaskService.complete(externalTask, mapOf("id" to entity.id))
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
