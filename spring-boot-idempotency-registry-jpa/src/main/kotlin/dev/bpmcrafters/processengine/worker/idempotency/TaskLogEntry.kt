package dev.bpmcrafters.processengine.worker.idempotency

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "task_log_entry_")
class TaskLogEntry(
  @Id
  @Column(name = "task_id_", nullable = false, length = 100)
  val taskId: String,
  @Column(name = "process_instance_id_", nullable = false, length = 100)
  val processInstanceId: String,
  @Column(name = "created_at_", nullable = false)
  val createdAt: Instant,
  @Column(name = "result_", nullable = false)
  @Lob
  @Convert(converter = MapConverter::class)
  val result: Map<String, Any?>
) {

  override fun toString(): String {
    return "TaskLogEntry(taskId='$taskId', processInstanceId='$processInstanceId', createdAt=$createdAt, result=$result)"
  }

}
