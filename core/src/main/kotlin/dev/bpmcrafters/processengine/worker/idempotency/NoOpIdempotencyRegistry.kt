package dev.bpmcrafters.processengine.worker.idempotency

import dev.bpmcrafters.processengineapi.task.TaskInformation

/**
 * No-op implementation of [IdempotencyRegistry].
 * @since 0.8.0
 **/
class NoOpIdempotencyRegistry : IdempotencyRegistry {

  /**
   * Discards the task result.
   **/
  override fun register(taskInformation: TaskInformation, result: Map<String, Any?>) {}

  /**
   * Returns null because this registry retains no task results.
   **/
  override fun getTaskResult(taskInformation: TaskInformation): Map<String, Any?>? = null

  /**
   * Accepts a task cleanup request without storing any state.
   **/
  override fun removeTaskResult(taskId: String) {}

  /**
   * Accepts a process-instance cleanup request without storing any state.
   **/
  override fun removeAllTaskResults(processInstanceId: String) {}

}
