package dev.bpmcrafters.processengine.worker.idempotency

import dev.bpmcrafters.processengineapi.task.TaskInformation
import java.util.concurrent.ConcurrentHashMap

/**
 * In-Memory local implementation of the registry.
 * @param enabled controls if the registry is enabled.
 */
class InMemoryIdempotencyRegistry(
  private val enabled: Boolean
) : IdempotencyRegistry {

  private val invocations = ConcurrentHashMap<String, Map<String, Any?>>()

  override fun register(
    taskInformation: TaskInformation,
    invocationResult: Map<String, Any?>
  ): Map<String, Any?> {
    invocations[taskInformation.taskId] = invocationResult
    return invocationResult
  }

  override fun hasTaskInformation(taskInformation: TaskInformation): Boolean {
    return enabled
      && invocations.containsKey(taskInformation.taskId)
  }

  override fun getResult(taskInformation: TaskInformation): Map<String, Any?> {
    return invocations.getValue(taskInformation.taskId)
  }


}
