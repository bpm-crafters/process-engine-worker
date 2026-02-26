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

}
