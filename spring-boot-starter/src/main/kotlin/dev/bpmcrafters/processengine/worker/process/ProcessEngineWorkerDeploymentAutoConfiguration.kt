package dev.bpmcrafters.processengine.worker.process

import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.support.ResourcePatternResolver

@Configuration
@EnableConfigurationProperties(ProcessEngineWorkerDeploymentProperties::class)
class ProcessEngineWorkerDeploymentAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  fun processDeployment(
    resourcePatternResolver: ResourcePatternResolver,
    deploymentApi: DeploymentApi,
    processEngineWorkerDeploymentProperties: ProcessEngineWorkerDeploymentProperties
  ): ProcessDeployment = ProcessDeployment(resourcePatternResolver, deploymentApi, processEngineWorkerDeploymentProperties)

  @Bean
  @ConditionalOnProperty(
    prefix = ProcessEngineWorkerDeploymentProperties.PREFIX,
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
  )
  fun autoDeploymentOnStartup(processDeployment: ProcessDeployment): AutoDeploymentOnStartup {
    return AutoDeploymentOnStartup(processDeployment)
  }

}
