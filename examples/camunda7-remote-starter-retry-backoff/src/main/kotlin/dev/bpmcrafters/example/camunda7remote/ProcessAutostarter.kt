package dev.bpmcrafters.example.camunda7remote

import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.community.rest.client.api.ProcessDefinitionApi
import org.camunda.community.rest.client.api.ProcessInstanceApi
import org.camunda.community.rest.client.model.StartProcessInstanceDto
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class ProcessAutostarter(
  val processDefinitionApi: ProcessDefinitionApi,
  val processInstanceApi: ProcessInstanceApi,
) {

  @Async
  @EventListener
  @Order(Integer.MAX_VALUE) // To make sure that our example process has been deployed.
  fun applicationStarted(ignore: ApplicationStartedEvent) {
    val getProcessInstancesResponse = processInstanceApi.getProcessInstances(null, null, null, null, null, null, null, null, null, "retry-backoff-example-process", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null)
    if (getProcessInstancesResponse.body?.size == 0) {
      logger.info { "No \"retry-backoff-example-process\" process instance found; starting a new one" }
      processDefinitionApi.startProcessInstanceByKey("retry-backoff-example-process", StartProcessInstanceDto())
    } else {
      logger.info { "\"retry-backoff-example-process\" process instance found; not starting a new one" }
    }
  }

}
