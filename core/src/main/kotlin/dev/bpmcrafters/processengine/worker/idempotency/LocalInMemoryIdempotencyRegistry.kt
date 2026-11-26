package dev.bpmcrafters.processengine.worker.idempotency

import dev.bpmcrafters.processengineapi.CommonRestrictions.PROCESS_INSTANCE_ID
import dev.bpmcrafters.processengineapi.task.TaskInformation
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Immediate, local storage. Not durable, clustered, or transaction-aware.
 * @since 0.9.0
 **/
class LocalInMemoryIdempotencyRegistry : IdempotencyRegistry {
  private val results = ConcurrentHashMap<String, Map<String, Any?>>()
  private val taskIdsByProcessInstanceId = ConcurrentHashMap<String, Queue<String>>()

  /**
   * Stores the task result immediately and indexes it by process instance.
   **/
  override fun register(taskInformation: TaskInformation, result: Map<String, Any?>) {
    results[taskInformation.taskId] = result
    taskIdsByProcessInstanceId
      .computeIfAbsent(taskInformation.meta[PROCESS_INSTANCE_ID] as String) { ConcurrentLinkedQueue() }
      .add(taskInformation.taskId)
  }

  /**
   * Returns the locally stored result for the task, if present.
   **/
  override fun getTaskResult(taskInformation: TaskInformation): Map<String, Any?>? = results[taskInformation.taskId]

  /**
   * Removes the locally stored result for one task.
   **/
  override fun removeTaskResult(taskId: String) {
    results.remove(taskId)
  }

  /**
   * Removes all locally stored results for the given process instance.
   **/
  override fun removeAllTaskResults(processInstanceId: String) {
    taskIdsByProcessInstanceId.remove(processInstanceId)?.forEach(results::remove)
  }
}
