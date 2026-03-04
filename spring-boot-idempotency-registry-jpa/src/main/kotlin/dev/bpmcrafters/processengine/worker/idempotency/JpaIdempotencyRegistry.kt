package dev.bpmcrafters.processengine.worker.idempotency

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.task.TaskInformation
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityManager
import java.time.Clock

private val logger = KotlinLogging.logger {}

class JpaIdempotencyRegistry(
  val entityManager: EntityManager,
  val clock: Clock = Clock.systemUTC()
) : IdempotencyRegistry {


  override fun register(taskInformation: TaskInformation, result: Map<String, Any?>) {
    if (entityManager.isJoinedToTransaction) {
      entityManager.persist(
        TaskLogEntry(
          taskInformation.taskId,
          taskInformation.meta[CommonRestrictions.PROCESS_INSTANCE_ID] as String,
          clock.instant(),
          result
        )
      )
    } else {
      logger.error { "No active transaction found. JPAIdempotencyRegistry must run inside the transaction, please make your worker transaction. The worker call is ignored." }
    }
  }

  override fun getTaskResult(taskInformation: TaskInformation): Map<String, Any?>? = entityManager
    .find(TaskLogEntry::class.java, taskInformation.taskId)
    ?.result

}
