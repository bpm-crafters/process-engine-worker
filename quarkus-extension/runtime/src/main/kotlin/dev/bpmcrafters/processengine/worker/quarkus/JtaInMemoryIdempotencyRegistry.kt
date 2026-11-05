package dev.bpmcrafters.processengine.worker.quarkus

import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.idempotency.LocalInMemoryIdempotencyRegistry
import dev.bpmcrafters.processengineapi.task.TaskInformation
import jakarta.transaction.Status
import jakarta.transaction.Synchronization
import jakarta.transaction.TransactionSynchronizationRegistry

/** Opt-in local retry cache. Mutations publish in order only after a successful commit. */
class JtaInMemoryIdempotencyRegistry(private val transactions: TransactionSynchronizationRegistry) : IdempotencyRegistry {
  private val storage: IdempotencyRegistry = LocalInMemoryIdempotencyRegistry()
  private val resourceKey = Any()

  override fun register(task: TaskInformation, result: Map<String, Any?>) {
    val snapshot = HashMap(result)
    val pending = pending()
    if (pending == null) {
      storage.register(task, snapshot)
      return
    }
    pending.results[task.taskId] = snapshot
    pending.processes[task.taskId] = processId(task)
    pending.removed.remove(task.taskId)
    pending.operations.add { storage.register(task, snapshot) }
  }

  override fun getTaskResult(task: TaskInformation): Map<String, Any?>? {
    val pending = pending()
    if (pending != null) {
      if (pending.results.containsKey(task.taskId)) return pending.results[task.taskId]
      if (task.taskId in pending.removed || processId(task) in pending.removedProcesses) return null
    }
    return storage.getTaskResult(task)
  }

  override fun removeTaskResult(taskId: String) {
    val pending = pending()
    if (pending == null) {
      storage.removeTaskResult(taskId)
      return
    }
    pending.results.remove(taskId)
    pending.removed.add(taskId)
    pending.operations.add { storage.removeTaskResult(taskId) }
  }

  override fun removeAllTaskResults(processInstanceId: String) {
    val pending = pending()
    if (pending == null) {
      storage.removeAllTaskResults(processInstanceId)
      return
    }
    pending.removedProcesses.add(processInstanceId)
    pending.processes.filterValues { it == processInstanceId }.keys.forEach(pending.results::remove)
    pending.operations.add { storage.removeAllTaskResults(processInstanceId) }
  }

  private fun pending(): Pending? {
    if (transactions.transactionKey == null) return null
    (transactions.getResource(resourceKey) as Pending?)?.let { return it }
    val pending = Pending()
    transactions.putResource(resourceKey, pending)
    transactions.registerInterposedSynchronization(object : Synchronization {
      override fun beforeCompletion() = Unit
      override fun afterCompletion(status: Int) {
        if (status == Status.STATUS_COMMITTED) pending.operations.forEach { it() }
      }
    })
    return pending
  }

  private fun processId(task: TaskInformation): String? = task.meta["processInstanceId"] as String?

  private class Pending {
    val results = HashMap<String, Map<String, Any?>>()
    val processes = HashMap<String, String?>()
    val removed = HashSet<String>()
    val removedProcesses = HashSet<String>()
    val operations = mutableListOf<() -> Unit>()
  }
}
