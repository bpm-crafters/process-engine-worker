package dev.bpmcrafters.processengine.worker.idempotency

import jakarta.persistence.*
import java.time.Instant

/**
 * Persists the result of a completed worker invocation for idempotent retries.
 * @property taskId Engine identifier of the external task.
 * @property processInstanceId Engine identifier of the process instance.
 * @property createdAt Time the result was stored.
 * @property result Worker result used to resume task completion.
 * @since 0.8.0
 **/
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

  /**
   * Returns a diagnostic description of the task log entry.
   **/
  override fun toString(): String {
    return "TaskLogEntry(taskId='$taskId', processInstanceId='$processInstanceId', createdAt=$createdAt, result=$result)"
  }

}
