package dev.bpmcrafters.processengine.worker.idempotency

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "task_log_entry_")
class TaskLogEntry(
  @Id
  @Column(name = "task_id_", nullable = false, length = 100)
  var taskId: String,
  @Column(name = "process_instance_id_", nullable = false, length = 100)
  var processInstanceId: String,
  @Column(name = "created_at_", nullable = false)
  var createdAt: Instant,
  @Column(name = "result_")
  @Convert(converter = TaskResultMapConverter::class)
  var result: Map<String, Any?>
) {

  override fun toString(): String {
    return "TaskLogEntry(taskId='$taskId', processInstanceId='$processInstanceId', createdAt=$createdAt, result=$result)"
  }

}
