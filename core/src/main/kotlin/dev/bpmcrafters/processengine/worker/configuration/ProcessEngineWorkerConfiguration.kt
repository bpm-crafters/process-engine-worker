package dev.bpmcrafters.processengine.worker.configuration

/**
 * Framework-independent configuration of the process engine worker registrar.
 * Framework integrations map their configuration properties (prefix [DEFAULT_PREFIX]) to this class.
 * @since 0.8.6
 */
data class ProcessEngineWorkerConfiguration(
  /**
   * Determines whether tasks are completed before transaction commit.
   */
  val completeTasksBeforeCommit: Boolean = false,
  /**
   * Indicates whether to automatically remove a task result when its task has been completed successfully.
   *
   * Leaving this turned on makes sure that task results are rarely left behind.
   * If turned on and under normal circumstances, task results can only get left behind
   * if the completion of a task was successful but the removal of a task result was not.
   */
  val removeTaskResultOnCompletion: Boolean = true,
  /**
   * Default tenant id to use for all workers.
   */
  val tenantId: String? = null,
) {
  companion object {
    /**
     * Prefix of the configuration properties of the worker.
     */
    const val DEFAULT_PREFIX = "dev.bpm-crafters.process-api.worker"

    /**
     * Name of the property switching the worker registration on or off. Defaults to `true`.
     */
    const val ENABLED_PROPERTY = "$DEFAULT_PREFIX.enabled"
  }
}
