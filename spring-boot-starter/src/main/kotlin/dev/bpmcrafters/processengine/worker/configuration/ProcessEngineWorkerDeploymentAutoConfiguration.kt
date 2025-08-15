package dev.bpmcrafters.processengine.worker.configuration

import dev.bpmcrafters.processengine.worker.process.AutoDeploymentOnStartup
import dev.bpmcrafters.processengine.worker.process.ProcessDeployment
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.core.io.support.ResourcePatternResolver

/**
 * Auto-configuration for auto-deployment.
 * 0.5.0
 */
@AutoConfiguration
@EnableConfigurationProperties(ProcessEngineWorkerDeploymentProperties::class)
class ProcessEngineWorkerDeploymentAutoConfiguration {

  /**
   * Create a process deployment bean, independent of the auto-deployment feature.
   */
  @Bean
  @ConditionalOnMissingBean
  fun processDeployment(
    resourcePatternResolver: ResourcePatternResolver,
    deploymentApi: DeploymentApi,
    processEngineWorkerDeploymentProperties: ProcessEngineWorkerDeploymentProperties
  ) = ProcessDeployment(
    resourcePatternResolver = resourcePatternResolver,
    deploymentApi = deploymentApi,
    processEngineWorkerDeploymentProperties = processEngineWorkerDeploymentProperties
  )

  /**
   * Configures a trigger for auto-deployment triggered by application start event.
   */
  @ConditionalOnProperty(
    prefix = ProcessEngineWorkerDeploymentProperties.PREFIX,
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
  )
  @Bean
  fun autoDeploymentOnStartup(processDeployment: ProcessDeployment) =
    AutoDeploymentOnStartup(processDeployment = processDeployment)

}
