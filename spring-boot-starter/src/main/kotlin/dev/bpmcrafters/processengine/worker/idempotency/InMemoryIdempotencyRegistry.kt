package dev.bpmcrafters.processengine.worker.idempotency

import dev.bpmcrafters.processengineapi.task.TaskInformation
import jakarta.transaction.TransactionSynchronizationRegistry
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory local implementation of the registry.
 */
class InMemoryIdempotencyRegistry : IdempotencyRegistry {

  private val results = ConcurrentHashMap<String, Map<String, Any?>>()

  override fun register(taskInformation: TaskInformation, result: Map<String, Any?>) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {

        override fun afterCommit() {
          results[taskInformation.taskId] = result
        }

      })
    } else {
      results[taskInformation.taskId] = result
    }
  }

  override fun getTaskResult(taskInformation: TaskInformation): Map<String, Any?>? = results[taskInformation.taskId]

}
