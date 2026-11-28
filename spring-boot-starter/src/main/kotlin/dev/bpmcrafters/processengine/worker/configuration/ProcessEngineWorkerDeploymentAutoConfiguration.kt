package dev.bpmcrafters.processengine.worker.configuration

import dev.bpmcrafters.processengine.worker.process.AutoDeploymentOnStartup
import dev.bpmcrafters.processengine.worker.process.ProcessDeployment
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.lifecycle.ProcessApplicationLifecycle
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Lazy
import org.springframework.core.io.support.ResourcePatternResolver

/**
 * Auto-configuration for auto-deployment.
 * @since 0.5.0
 **/
@AutoConfiguration
@EnableConfigurationProperties(ProcessEngineWorkerDeploymentProperties::class)
class ProcessEngineWorkerDeploymentAutoConfiguration {

  /**
   * Create a process deployment bean, independent of the auto-deployment feature.
   **/
  @Bean
  @ConditionalOnMissingBean
  fun processDeployment(
    resourcePatternResolver: ResourcePatternResolver,
    deploymentApi: ObjectProvider<DeploymentApi>,
    processEngineWorkerDeploymentProperties: ProcessEngineWorkerDeploymentProperties
  ) = ProcessDeployment(
    resourcePatternResolver = resourcePatternResolver,
    deploymentApiProvider = { deploymentApi.ifAvailable },
    deploymentProperties = processEngineWorkerDeploymentProperties
  )

  /**
   * Registers auto-deployment for the process application startup lifecycle.
   **/
  @ConditionalOnProperty(
    prefix = ProcessEngineWorkerDeploymentProperties.DEFAULT_PREFIX,
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
  )
  @Bean
  @Lazy(false)
  fun autoDeploymentOnStartup(
    processDeployment: ProcessDeployment,
    lifecycle: ProcessApplicationLifecycle
  ) = AutoDeploymentOnStartup(processDeployment).also(lifecycle::register)

}
