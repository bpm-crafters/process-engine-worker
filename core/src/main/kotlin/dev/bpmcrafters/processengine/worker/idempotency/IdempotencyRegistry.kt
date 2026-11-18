package dev.bpmcrafters.processengine.worker.idempotency

import dev.bpmcrafters.processengineapi.task.TaskInformation

/**
 * Idempotency registry used to avoid duplicate worker invocations.
 * @since 0.8.0
 */
interface IdempotencyRegistry {

  /**
   * Registers the result of a task invocation.
   * @param taskInformation the to register the result for.
   * @param result the result of worker invocation.
   */
  fun register(taskInformation: TaskInformation, result: Map<String, Any?>)

  /**
   * Gets the result of a task invocation.
   * @param taskInformation the task to get the result for.
   * @return the result payload of a previous worker invocation if it exists.
   */
  fun getTaskResult(taskInformation: TaskInformation): Map<String, Any?>?

  /**
   * Deletes the result of a single task.
   *
   * If enabled, this method is automatically called when a task completion call returns successfully.
   *
   * @param taskId the ID of the task to remove the result for.
   */
  fun removeTaskResult(taskId: String)

  /**
   * Removes all task results of a process instance.
   *
   * This method should be called when a process instance has completed to ensure that every result has been removed.
   *
   * @param processInstanceId the ID of the process instance to remove task results for.
   */
  fun removeAllTaskResults(processInstanceId: String)

}
