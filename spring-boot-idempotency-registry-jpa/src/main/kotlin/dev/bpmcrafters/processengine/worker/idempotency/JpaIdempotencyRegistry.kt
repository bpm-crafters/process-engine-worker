package dev.bpmcrafters.processengine.worker.idempotency

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.springframework.data.repository.findByIdOrNull
import java.time.Clock

/**
 * JPA Idempotency Registry implementation.
 * @since 0.8.0
 **/
class JpaIdempotencyRegistry(
  val taskLogEntryRepository: TaskLogEntryRepository,
  val clock: Clock = Clock.systemUTC()
) : IdempotencyRegistry {

  /**
   * Persists the result and its process instance association.
   **/
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

  /**
   * Looks up the persisted result for the task.
   **/
  override fun getTaskResult(taskInformation: TaskInformation): Map<String, Any?>? = taskLogEntryRepository
    .findByIdOrNull(taskInformation.taskId)
    ?.result

  /**
   * Deletes the persisted result for one task.
   **/
  override fun removeTaskResult(taskId: String) {
    taskLogEntryRepository.deleteByTaskId(taskId)
  }

  /**
   * Deletes persisted results associated with the given process instance.
   **/
  override fun removeAllTaskResults(processInstanceId: String) {
    taskLogEntryRepository.deleteByProcessInstanceId(processInstanceId)
  }

}
