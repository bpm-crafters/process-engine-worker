package dev.bpmcrafters.processengine.worker.idempotency

import dev.bpmcrafters.processengineapi.task.TaskInformation
import java.util.concurrent.ConcurrentHashMap

/**
 * In-Memory local implementation of the registry.
 */
class InMemoryIdempotencyRegistry(
  private val enableIdempotencyRegistry: Boolean
) : IdempotencyRegistry {

  companion object {
    private val NULL_SENTINEL = Any()
  }

  private val invocations = ConcurrentHashMap<String, Any>()

  override fun register(
    taskInformation: TaskInformation,
    invocationResult: Any?
  ): Any? {
    invocations[taskInformation.taskId] = invocationResult ?: NULL_SENTINEL
    return invocationResult
  }

  override fun hasTaskInformation(taskInformation: TaskInformation): Boolean {
    return enableIdempotencyRegistry
      && invocations.containsKey(taskInformation.taskId)
  }

  override fun getResult(taskInformation: TaskInformation): Any? {
    val value = invocations[taskInformation.taskId]
    return if (value === NULL_SENTINEL) {
      null
    } else {
      value
    }
  }


}
