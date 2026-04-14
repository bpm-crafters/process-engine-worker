package dev.bpmcrafters.processengine.worker.idempotency

import org.springframework.data.repository.CrudRepository
import org.springframework.transaction.annotation.Transactional

/**
 * JPA Repository for task log entry.
 */
@Transactional
interface TaskLogEntryRepository : CrudRepository<TaskLogEntry, String> {

  fun deleteByTaskId(taskId: String)

  fun deleteByProcessInstanceId(processInstanceId: String)

}
