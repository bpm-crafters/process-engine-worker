package dev.bpmcrafters.processengine.worker.idempotency

import dev.bpmcrafters.processengine.worker.transaction.AfterCommitHook
import dev.bpmcrafters.processengine.worker.transaction.AfterCommitHookAware
import dev.bpmcrafters.processengineapi.CommonRestrictions.PROCESS_INSTANCE_ID
import dev.bpmcrafters.processengineapi.task.TaskInformation
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * In-memory local implementation of the registry.
 *
 * Usage is discouraged because it can only take into account a single process instance.
 * I.e., don't use it in a clustered environment.
 *
 * If a transaction is active during registration, the registration is deferred until the transaction has been
 * committed, using the [afterCommitHook]. The hook is installed by the framework integration (Spring Boot starter,
 * Quarkus extension); without a framework integration the registration happens immediately.
 */
class InMemoryIdempotencyRegistry(
  override var afterCommitHook: AfterCommitHook = AfterCommitHook.IMMEDIATE
) : IdempotencyRegistry, AfterCommitHookAware {

  private val results = ConcurrentHashMap<String, Map<String, Any?>>()

  private val taskIdsByProcessInstanceId = ConcurrentHashMap<String, Queue<String>>()

  override fun register(taskInformation: TaskInformation, result: Map<String, Any?>) {
    afterCommitHook.afterCommitOrNow {
      doRegister(taskInformation, result)
    }
  }

  private fun doRegister(taskInformation: TaskInformation, result: Map<String, Any?>) {
    results[taskInformation.taskId] = result
    taskIdsByProcessInstanceId
      .computeIfAbsent(taskInformation.meta[PROCESS_INSTANCE_ID] as String)
      { ConcurrentLinkedQueue() }
      .add(taskInformation.taskId)
  }

  override fun getTaskResult(taskInformation: TaskInformation): Map<String, Any?>? = results[taskInformation.taskId]

  override fun removeTaskResult(taskId: String) {
    results.remove(taskId)
  }

  override fun removeAllTaskResults(processInstanceId: String) {
    val taskIds = taskIdsByProcessInstanceId.remove(processInstanceId)
    taskIds?.forEach(results::remove)
  }

}
