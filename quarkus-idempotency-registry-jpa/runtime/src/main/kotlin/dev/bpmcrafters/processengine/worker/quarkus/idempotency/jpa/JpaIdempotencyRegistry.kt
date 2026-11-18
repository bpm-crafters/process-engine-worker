package dev.bpmcrafters.processengine.worker.quarkus.idempotency.jpa

import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.task.TaskInformation
import io.quarkus.arc.DefaultBean
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.narayana.jta.TransactionRunner
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.transaction.Status
import java.time.Clock

/** A retry-result cache, not an atomic duplicate-execution lock. */
@ApplicationScoped
@DefaultBean
@Priority(100)
class JpaIdempotencyRegistry @Inject constructor(
  private val entityManager: EntityManager,
  clocks: Instance<Clock>
) : IdempotencyRegistry {
  private val clock: Clock = if (clocks.isUnsatisfied) Clock.systemUTC() else clocks.get()

  override fun register(taskInformation: TaskInformation, result: Map<String, Any?>) {
    val processInstanceId = taskInformation.meta[CommonRestrictions.PROCESS_INSTANCE_ID] as? String
      ?: throw IllegalArgumentException("Task metadata must contain a String process instance ID")
    // Merge preserves re-registration/update semantics. Keep the original concrete result map.
    transaction().run {
      entityManager.merge(TaskLogEntry(taskInformation.taskId, processInstanceId, clock.instant(), result))
    }
  }

  override fun getTaskResult(taskInformation: TaskInformation): Map<String, Any?>? =
    transaction().call {
      entityManager.find(TaskLogEntry::class.java, taskInformation.taskId)?.getResult()
    }

  override fun removeTaskResult(taskId: String) {
    transaction().run {
      entityManager.find(TaskLogEntry::class.java, taskId)?.let(entityManager::remove)
    }
  }

  override fun removeAllTaskResults(processInstanceId: String) {
    transaction().run {
      entityManager.createQuery(
        "from WorkerTaskLogEntry entry where entry.processInstanceId = :id", TaskLogEntry::class.java
      ).setParameter("id", processInstanceId).resultList.forEach(entityManager::remove)
    }
  }

  private fun transaction(): TransactionRunner {
    val status = QuarkusTransaction.getStatus()
    if (status == Status.STATUS_ACTIVE || status == Status.STATUS_MARKED_ROLLBACK) {
      return QuarkusTransaction.joiningExisting()
    }
    // An afterCompletion callback can still have a completed transaction associated.
    // Suspend it before writing; never suspend an active business transaction.
    return QuarkusTransaction.requiringNew()
  }
}
