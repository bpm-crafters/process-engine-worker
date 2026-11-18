package dev.bpmcrafters.processengine.worker.quarkus.idempotency.jpa

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity(name = "WorkerTaskLogEntry")
@Table(name = "task_log_entry_")
open class TaskLogEntry(
  @field:Id
  @field:Column(name = "task_id_", nullable = false, length = 100)
  private var taskId: String,
  @field:Column(name = "process_instance_id_", nullable = false, length = 100)
  private var processInstanceId: String,
  @field:Column(name = "created_at_", nullable = false)
  private var createdAt: Instant,
  result: Map<String, Any?>
) {
  @field:Column(name = "result_")
  @field:JdbcTypeCode(SqlTypes.LONGVARBINARY)
  @field:Convert(converter = TaskResultMapConverter::class)
  private var result: Map<String, Any?>? = result

  open fun getResult(): Map<String, Any?> = result ?: emptyMap()
}
