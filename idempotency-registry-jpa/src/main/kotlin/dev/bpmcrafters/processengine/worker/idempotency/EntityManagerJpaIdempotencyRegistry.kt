package dev.bpmcrafters.processengine.worker.idempotency

import dev.bpmcrafters.processengine.worker.transaction.TransactionalExecutor
import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.task.TaskInformation
import jakarta.persistence.EntityManager
import java.time.Clock
import java.util.function.Supplier

/**
 * JPA-based idempotency registry using a plain [EntityManager], independent of any framework.
 *
 * All writes are executed via the [TransactionalExecutor] with `REQUIRED` semantics: they join the transaction of a transactional
 * worker (so that the result is rolled back together with the business data) or run in an own transaction otherwise.
 *
 * @param entityManager supplier of the entity manager to use (e.g. a container-managed shared entity manager).
 * @param transactionalExecutor transaction management.
 * @param clock clock used for the creation timestamp.
 * @since 0.8.6
 */
class EntityManagerJpaIdempotencyRegistry(
  private val entityManager: Supplier<EntityManager>,
  private val transactionalExecutor: TransactionalExecutor,
  private val clock: Clock = Clock.systemUTC()
) : IdempotencyRegistry {

  override fun register(taskInformation: TaskInformation, result: Map<String, Any?>) {
    transactionalExecutor.executeInTransaction {
      val em = entityManager.get()
      val entry = TaskLogEntry(
        taskInformation.taskId,
        requireNotNull(taskInformation.meta[CommonRestrictions.PROCESS_INSTANCE_ID]) { "Task information of ${taskInformation.taskId} must contain the process instance id." },
        clock.instant(),
        result
      )
      if (em.find(TaskLogEntry::class.java, taskInformation.taskId) == null) {
        em.persist(entry)
      } else {
        em.merge(entry)
      }
      em.flush()
    }
  }

  override fun getTaskResult(taskInformation: TaskInformation): Map<String, Any?>? =
    transactionalExecutor.executeInTransaction {
      entityManager.get().find(TaskLogEntry::class.java, taskInformation.taskId)?.result
    }

  override fun removeTaskResult(taskId: String) {
    transactionalExecutor.executeInTransaction {
      entityManager.get()
        .createQuery("delete from TaskLogEntry e where e.taskId = :taskId")
        .setParameter("taskId", taskId)
        .executeUpdate()
    }
  }

  override fun removeAllTaskResults(processInstanceId: String) {
    transactionalExecutor.executeInTransaction {
      entityManager.get()
        .createQuery("delete from TaskLogEntry e where e.processInstanceId = :processInstanceId")
        .setParameter("processInstanceId", processInstanceId)
        .executeUpdate()
    }
  }
}
