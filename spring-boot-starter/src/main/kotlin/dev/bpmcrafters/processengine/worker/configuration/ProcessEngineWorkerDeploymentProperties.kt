package dev.bpmcrafters.processengine.worker.configuration

import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerDeploymentProperties.Companion.DEFAULT_PREFIX
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Configuration properties for the deployment feature.
 */
@Validated
@ConfigurationProperties(prefix = DEFAULT_PREFIX)
data class ProcessEngineWorkerDeploymentProperties(
  /**
   * Pattern to scan for BPMN resources.
   */
  var bpmnResourcePattern: String = "classpath*:/**/*.bpmn",
  /**
   * Pattern to scan fo DMN resources.
   */
  var dmnResourcePattern: String = "classpath*:/**/*.dmn",
  /**
   * Flag to control if the feature is active, defaults to `false`.
   */
  var enabled: Boolean = false,
  /**
   * Deployment timeout, defaults to 30 seconds.
   */
  var deploymentTimeoutInSeconds: Long = 30,
) {
  companion object {
    const val DEFAULT_PREFIX = ProcessEngineWorkerProperties.DEFAULT_PREFIX + ".deployment"
  }
}
