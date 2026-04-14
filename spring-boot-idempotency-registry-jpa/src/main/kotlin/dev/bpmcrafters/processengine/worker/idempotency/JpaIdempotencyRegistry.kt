package dev.bpmcrafters.processengine.worker.idempotency

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.springframework.data.repository.findByIdOrNull
import java.time.Clock

/**
 * JPA Idempotency Registry implementation.
 * @since 0.8.0
 */
class JpaIdempotencyRegistry(
  val taskLogEntryRepository: TaskLogEntryRepository,
  val clock: Clock = Clock.systemUTC()
) : IdempotencyRegistry {

  override fun register(taskInformation: TaskInformation, result: Map<String, Any?>) {
    taskLogEntryRepository.save(
      TaskLogEntry(
        taskInformation.taskId,
        taskInformation.meta[CommonRestrictions.PROCESS_INSTANCE_ID] as String,
        clock.instant(),
        result
      )
    )
  }

  override fun getTaskResult(taskInformation: TaskInformation): Map<String, Any?>? = taskLogEntryRepository
    .findByIdOrNull(taskInformation.taskId)
    ?.result

  override fun removeTaskResult(taskId: String) {
    taskLogEntryRepository.deleteByTaskId(taskId)
  }

  override fun removeAllTaskResults(processInstanceId: String) {
    taskLogEntryRepository.deleteByProcessInstanceId(processInstanceId)
  }

}
