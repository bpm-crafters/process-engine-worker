package dev.bpmcrafters.processengine.worker.idempotency

import dev.bpmcrafters.processengineapi.task.TaskInformation

class NoOpIdempotencyRegistry : IdempotencyRegistry {

  override fun register(taskInformation: TaskInformation, result: Map<String, Any?>) {}

  override fun getTaskResult(taskInformation: TaskInformation): Map<String, Any?>? = null

}
