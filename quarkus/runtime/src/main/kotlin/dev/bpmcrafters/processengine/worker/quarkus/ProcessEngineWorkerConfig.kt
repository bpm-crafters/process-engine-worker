package dev.bpmcrafters.processengine.worker.quarkus

import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerConfiguration
import io.quarkus.arc.Unremovable
import io.smallrye.config.ConfigMapping
import io.smallrye.config.WithDefault
import java.util.Optional

/**
 * Quarkus configuration of the process engine worker, bound to the prefix [ProcessEngineWorkerConfiguration.DEFAULT_PREFIX].
 * The keys are identical to the Spring Boot configuration (`enabled`, `complete-tasks-before-commit`,
 * `remove-task-result-on-completion`, `tenant-id`).
 * @since 0.8.6
 */
@ConfigMapping(prefix = ProcessEngineWorkerConfiguration.DEFAULT_PREFIX)
@Unremovable
interface ProcessEngineWorkerConfig {

  /**
   * Switches the registration of the process engine workers on or off. Defaults to `true`.
   */
  @WithDefault("true")
  fun enabled(): Boolean

  /**
   * Determines whether tasks are completed before transaction commit. Defaults to `false`.
   */
  @WithDefault("false")
  fun completeTasksBeforeCommit(): Boolean

  /**
   * Indicates whether to automatically remove a task result from the idempotency registry when its task has been
   * completed successfully. Defaults to `true`.
   */
  @WithDefault("true")
  fun removeTaskResultOnCompletion(): Boolean

  /**
   * Default tenant id to use for all workers.
   */
  fun tenantId(): Optional<String>
}

/**
 * Maps the Quarkus configuration to the framework-independent configuration of the worker registrar.
 * @return configuration.
 * @since 0.8.6
 */
fun ProcessEngineWorkerConfig.toConfiguration(): ProcessEngineWorkerConfiguration = ProcessEngineWorkerConfiguration(
  completeTasksBeforeCommit = completeTasksBeforeCommit(),
  removeTaskResultOnCompletion = removeTaskResultOnCompletion(),
  tenantId = tenantId().filter { it.isNotBlank() }.orElse(null),
)
