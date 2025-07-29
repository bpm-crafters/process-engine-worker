package dev.bpmcrafters.processengine.worker.process

import dev.bpmcrafters.processengineapi.deploy.DeployBundleCommand
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.deploy.NamedResource
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.io.Resource
import org.springframework.core.io.support.ResourcePatternResolver
import java.io.IOException
import java.util.concurrent.TimeUnit

val logger = KotlinLogging.logger {}

open class ProcessDeployment(
  private val resourcePatternResolver: ResourcePatternResolver,
  private val deploymentApi: DeploymentApi,
  private val processEngineWorkerDeploymentProperties: ProcessEngineWorkerDeploymentProperties
) {

  open fun deployResources() {
    val resources = ArrayList<Resource>()
    try {
      resources.addAll(resourcePatternResolver.getResources(processEngineWorkerDeploymentProperties.bpmnResourcePattern))
      resources.addAll(resourcePatternResolver.getResources(processEngineWorkerDeploymentProperties.dmnResourcePattern))
    } catch(e: IOException) {
      logger.warn(e) { "Failed to load resources for deployment" }
    }

    resources.forEach { resource ->
      try {
        val deploymentResult = deploymentApi.deploy(DeployBundleCommand(
          listOf(NamedResource(resource.filename, resource.inputStream))
        )).get(30, TimeUnit.SECONDS)
        logger.info { "Deployed: ${resource.filename} with key ${deploymentResult.deploymentKey}" }
      } catch (e: Exception) {
        logger.error(e) { "Failed to deploy resource: ${resource.filename}" }
      }
    }
  }

}
