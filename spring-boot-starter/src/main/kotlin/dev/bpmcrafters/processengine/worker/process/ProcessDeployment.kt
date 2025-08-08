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
 */
open class ProcessDeployment(
    private val resourcePatternResolver: ResourcePatternResolver,
    private val deploymentApi: DeploymentApi,
    private val processEngineWorkerDeploymentProperties: ProcessEngineWorkerDeploymentProperties
) {

    /**
     * Deploys resources, configured via properties.
     */
    open fun deployResources(): DeploymentInformation? {
        val namedResources = buildList<Resource> {
            try {
                addAll(resourcePatternResolver.getResources(processEngineWorkerDeploymentProperties.bpmnResourcePattern))
                addAll(resourcePatternResolver.getResources(processEngineWorkerDeploymentProperties.dmnResourcePattern))
            } catch (e: IOException) {
                logger.warn(e) { "PROCESS-ENGINE-WORKER-051: Failed to load resources for deployment." }
            }
        }.map { resource -> NamedResource(resource.filename ?: "unknown", resource.inputStream) }

        if (namedResources.isEmpty()) {
            return null
        }

        return deploymentApi.deploy(
            DeployBundleCommand(resources = namedResources)
        ).get(processEngineWorkerDeploymentProperties.deploymentTimeoutInSeconds, TimeUnit.SECONDS)
            .also { deploymentResult ->
                logger.info { "PROCESS-ENGINE-WORKER-050: Deployed ${namedResources.size} resources with key ${deploymentResult.deploymentKey}." }
            }
    }
}


