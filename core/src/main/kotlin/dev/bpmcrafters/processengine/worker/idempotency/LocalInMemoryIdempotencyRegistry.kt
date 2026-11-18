package dev.bpmcrafters.processengine.worker.idempotency

import dev.bpmcrafters.processengineapi.CommonRestrictions.PROCESS_INSTANCE_ID
import dev.bpmcrafters.processengineapi.task.TaskInformation
import java.util.Queue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/** Immediate, local storage. Not durable, clustered, or transaction-aware. */
class LocalInMemoryIdempotencyRegistry : IdempotencyRegistry {
  private val results = ConcurrentHashMap<String, Map<String, Any?>>()
  private val taskIdsByProcessInstanceId = ConcurrentHashMap<String, Queue<String>>()

  override fun register(taskInformation: TaskInformation, result: Map<String, Any?>) {
    results[taskInformation.taskId] = result
    taskIdsByProcessInstanceId
      .computeIfAbsent(taskInformation.meta[PROCESS_INSTANCE_ID] as String) { ConcurrentLinkedQueue() }
      .add(taskInformation.taskId)
  }

  override fun getTaskResult(taskInformation: TaskInformation): Map<String, Any?>? = results[taskInformation.taskId]

  override fun removeTaskResult(taskId: String) {
    results.remove(taskId)
  }

  override fun removeAllTaskResults(processInstanceId: String) {
    taskIdsByProcessInstanceId.remove(processInstanceId)?.forEach(results::remove)
  }
}
