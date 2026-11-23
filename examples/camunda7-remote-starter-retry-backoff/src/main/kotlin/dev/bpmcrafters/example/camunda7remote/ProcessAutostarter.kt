package dev.bpmcrafters.example.camunda7remote

import dev.bpmcrafters.processengineapi.deploy.DeployBundleCommand
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.deploy.NamedResource
import dev.bpmcrafters.processengineapi.springboot.lifecycle.ProcessApplicationRunningEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import org.camunda.community.rest.client.api.ProcessDefinitionApi
import org.camunda.community.rest.client.api.ProcessInstanceApi
import org.camunda.community.rest.client.model.StartProcessInstanceDto
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class ProcessAutostarter(
  val deploymentApi: DeploymentApi,
  val processDefinitionApi: ProcessDefinitionApi,
  val processInstanceApi: ProcessInstanceApi,
) {

  @EventListener
  fun applicationStarted(ignore: ProcessApplicationRunningEvent) {
    deploymentApi.deploy(DeployBundleCommand(listOf(NamedResource.fromClasspath("bpmn/retry-backoff-example-process.bpmn"))))
      .join()

    val getProcessInstancesResponse = processInstanceApi.getProcessInstances(null, null, null, null, null, null, null, null, null, "retry-backoff-example-process", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null)
    if (getProcessInstancesResponse.body?.size == 0) {
      logger.info { "No \"retry-backoff-example-process\" process instance found; starting a new one" }
      processDefinitionApi.startProcessInstanceByKey("retry-backoff-example-process", StartProcessInstanceDto())
    } else {
      logger.info { "\"retry-backoff-example-process\" process instance found; not starting a new one" }
    }
  }

}
