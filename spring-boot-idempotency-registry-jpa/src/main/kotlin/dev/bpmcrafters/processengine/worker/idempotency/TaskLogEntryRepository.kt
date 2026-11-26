package dev.bpmcrafters.processengine.worker.idempotency

import org.springframework.data.repository.CrudRepository
import org.springframework.transaction.annotation.Transactional

/**
 * JPA Repository for task log entry.
 * @since 0.8.0
 **/
@Transactional
interface TaskLogEntryRepository : CrudRepository<TaskLogEntry, String> {

  /**
   * Deletes the log entry for one task.
   **/
  fun deleteByTaskId(taskId: String)

  /**
   * Deletes all log entries associated with the given process instance.
   **/
  fun deleteByProcessInstanceId(processInstanceId: String)

}
