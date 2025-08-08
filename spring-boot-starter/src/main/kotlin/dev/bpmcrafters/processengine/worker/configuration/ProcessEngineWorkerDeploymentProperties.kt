package dev.bpmcrafters.processengine.worker.configuration

import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerDeploymentProperties.Companion.PREFIX
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for the deployment feature.
 */
@ConfigurationProperties(prefix = PREFIX)
class ProcessEngineWorkerDeploymentProperties(
  /**
   * Pattern to scan for BPMN resources.
   */
  val bpmnResourcePattern: String = "classpath*:/**/*.bpmn",
  /**
   * Pattern to scan fo DMN resources.
   */
  val dmnResourcePattern: String = "classpath*:/**/*.dmn",
  /**
   * Flag to control if the feature is active, defaults to `false`.
   */
  val enabled: Boolean = false,
  /**
   * Deployment timeout, defaults to 30 seconds.
   */
  val deploymentTimeoutInSeconds: Long = 30,
) {
  companion object {
    const val PREFIX = ProcessEngineWorkerProperties.PREFIX + ".deployment"
  }
}
