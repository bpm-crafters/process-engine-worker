package dev.bpmcrafters.processengine.worker.process

import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerDeploymentProperties
import dev.bpmcrafters.processengineapi.deploy.DeployBundleCommand
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.deploy.DeploymentInformation
import dev.bpmcrafters.processengineapi.deploy.NamedResource
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.io.Resource
import org.springframework.core.io.support.ResourcePatternResolver
import java.io.IOException
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Implements configuration of the deployment.
 * @since 0.5.0
 **/
open class ProcessDeployment internal constructor(
  private val resourcePatternResolver: ResourcePatternResolver,
  private val deploymentApiProvider: () -> DeploymentApi?,
  private val deploymentProperties: ProcessEngineWorkerDeploymentProperties
) {

  constructor(
    resourcePatternResolver: ResourcePatternResolver,
    deploymentApi: DeploymentApi,
    deploymentProperties: ProcessEngineWorkerDeploymentProperties
  ) : this(resourcePatternResolver, { deploymentApi }, deploymentProperties)

  /**
   * Deploys resources, configured via properties.
   **/
  open fun deployResources(): DeploymentInformation? {
    val deploymentApi = deploymentApiProvider() ?: return null
    logger.info { "PROCESS-ENGINE-WORKER-052: Deploying resource(s)." }
    val namedResources = buildList<Resource> {
      try {
        addAll(resourcePatternResolver.getResources(deploymentProperties.bpmnResourcePattern))
        addAll(resourcePatternResolver.getResources(deploymentProperties.dmnResourcePattern))
      } catch (e: IOException) {
        logger.warn(e) { "PROCESS-ENGINE-WORKER-051: Failed to load resource(s) for deployment." }
      }
    }.map { resource -> NamedResource(resource.filename ?: "unknown", resource.inputStream) }

    if (namedResources.isEmpty()) {
      return null
    }

    return deploymentApi.deploy(
      DeployBundleCommand(resources = namedResources)
    ).get(deploymentProperties.deploymentTimeoutInSeconds, TimeUnit.SECONDS)
      .also { deploymentResult ->
        logger.info { "PROCESS-ENGINE-WORKER-050: Deployed ${namedResources.size} resource(s) with key ${deploymentResult.deploymentKey}." }
      }
  }
}


