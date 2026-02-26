package dev.bpmcrafters.processengine.worker.configuration

import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerProperties.Companion.DEFAULT_PREFIX
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Configuration properties allowing simple switch off/on of auto-registration.
 */
@Validated
@ConfigurationProperties(prefix = DEFAULT_PREFIX)
data class ProcessEngineWorkerProperties(
  /**
   * Determines whether the workers are automatically registered.
   */
  var registerProcessWorkers: Boolean = true,
  /**
   * Determines whether tasks are completed before transaction commit.
   */
  var completeTasksBeforeCommit: Boolean = false,
) {
  companion object {
    const val DEFAULT_PREFIX = "dev.bpm-crafters.process-api.worker"
  }
}
