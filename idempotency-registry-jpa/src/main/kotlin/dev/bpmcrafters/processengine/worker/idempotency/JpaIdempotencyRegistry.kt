package dev.bpmcrafters.processengine.worker.idempotency

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.task.TaskInformation
import jakarta.persistence.EntityManager
import java.time.Clock

class JpaIdempotencyRegistry(val entityManager: EntityManager) : IdempotencyRegistry {

  override fun register(taskInformation: TaskInformation, result: Map<String, Any?>) {
    entityManager.persist(TaskLogEntry(
      taskInformation.taskId,
      taskInformation.meta[CommonRestrictions.PROCESS_INSTANCE_ID] as String,
      Clock.systemUTC().instant(),
      result
    ))
  }

  override fun getTaskResult(taskInformation: TaskInformation): Map<String, Any?>? = entityManager
    .find(TaskLogEntry::class.java, taskInformation.taskId)
    ?.result

}
