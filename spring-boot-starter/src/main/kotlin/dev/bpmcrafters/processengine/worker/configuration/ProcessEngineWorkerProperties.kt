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
  /**
   * Indicates whether to automatically remove a task result when its task has been completed successfully.
   *
   * Leaving this turned on makes sure that task results are rarely left behind.
   * If turned on and under normal circumstances, task results can only get left behind
   * if the completion of a task was successful but the removal of a task result was not.
   */
  var removeTaskResultOnCompletion: Boolean = true,
) {
  companion object {
    const val DEFAULT_PREFIX = "dev.bpm-crafters.process-api.worker"
  }
}
