package dev.bpmcrafters.processengine.worker.idempotency

import dev.bpmcrafters.processengineapi.task.TaskInformation

/**
 * Idempotency registry used to avoid duplicate worker invocations.
 * @since 0.8.0
 */
interface IdempotencyRegistry {

  /**
   * Registers invocation of a task information and invocation result.
   * @param taskInformation task information holding the task attributes.
   * @param invocationResult result of worker invocation.
   * @return future of invocation, after registration.
   */
  fun register(taskInformation: TaskInformation, invocationResult: Any?): Any?

  /**
   * Checks if the invocation is registered.
   * @param taskInformation information of the task.
   * @return true, if already registered.
   */
  fun hasTaskInformation(taskInformation: TaskInformation): Boolean

  /**
   * Retrieves result of invocation.
   * @param taskInformation task information.
   * @return result.
   */
  fun getResult(taskInformation: TaskInformation): Any?
}
