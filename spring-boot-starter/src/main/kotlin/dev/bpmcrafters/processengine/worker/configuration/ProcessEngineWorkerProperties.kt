package dev.bpmcrafters.processengine.worker.configuration

import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerProperties.Companion.PREFIX
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties allowing simple switch off/on of auto-registration.
 */
@ConfigurationProperties(prefix = PREFIX)
data class ProcessEngineWorkerProperties(
  val registerProcessWorkers: Boolean = true,
  val completeTasksInTransaction: Boolean = true,
) {
  companion object {
    const val PREFIX = "dev.bpm-crafters.process-api.worker"
  }
}
