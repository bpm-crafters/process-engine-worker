package dev.bpmcrafters.processengine.worker.idempotency

import org.springframework.data.repository.CrudRepository

/**
 * JPA Repository for task log entry.
 */
interface TaskLogEntryRepository : CrudRepository<TaskLogEntry, String> {

  fun deleteByProcessInstanceId(processInstanceId: String)

}
