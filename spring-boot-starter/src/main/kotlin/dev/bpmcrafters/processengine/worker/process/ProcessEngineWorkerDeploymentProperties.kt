package dev.bpmcrafters.processengine.worker.process

import dev.bpmcrafters.processengine.worker.process.ProcessEngineWorkerDeploymentProperties.Companion.PREFIX
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = PREFIX)
class ProcessEngineWorkerDeploymentProperties(
  val bpmnResourcePattern: String = "classpath*:/**/*.bpmn",
  val dmnResourcePattern: String = "classpath*:/**/*.dmn",
  val enabled: Boolean = false
) {
  companion object {
    const val PREFIX = "dev.bpm-crafters.process-api.worker.deployment"
  }
}
