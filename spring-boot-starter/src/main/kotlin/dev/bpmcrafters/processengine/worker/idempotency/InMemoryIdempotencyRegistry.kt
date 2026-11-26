package dev.bpmcrafters.processengine.worker.idempotency

import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * In-memory local implementation of the registry.
 *
 * Usage is discouraged because it can only take into account a single process instance.
 * I.e., don't use it in a clustered environment.
 */
class InMemoryIdempotencyRegistry : IdempotencyRegistry {

  private val delegate = LocalInMemoryIdempotencyRegistry()

  override fun register(taskInformation: TaskInformation, result: Map<String, Any?>) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {

        override fun afterCommit() {
          delegate.register(taskInformation, result)
        }

      })
    } else {
      delegate.register(taskInformation, result)
    }
  }

  override fun getTaskResult(taskInformation: TaskInformation): Map<String, Any?>? = delegate.getTaskResult(taskInformation)

  override fun removeTaskResult(taskId: String) {
    delegate.removeTaskResult(taskId)
  }

  override fun removeAllTaskResults(processInstanceId: String) {
    delegate.removeAllTaskResults(processInstanceId)
  }

}
