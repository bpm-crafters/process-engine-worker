package dev.bpmcrafters.processengine.worker.configuration

import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerProperties.Companion.DEFAULT_PREFIX
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

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
  /**
   * Default tenant id to use for all workers.
   */
  var tenantId: String? = null,
  /**
   * Throwables that should be retried with a backoff. The retry counter for tasks that are being backed off from will not be decreased.
   *
   * Be careful with this setting, as it can lead to infinite loops.
   * Warnings will be logged whenever a task is being backed off from so you can monitor and resolve issues.
   */
  var backoffExceptions: MutableMap<Class<out Throwable>, Duration> = mutableMapOf()
) {
  companion object {
    const val DEFAULT_PREFIX = "dev.bpm-crafters.process-api.worker"
  }
}
