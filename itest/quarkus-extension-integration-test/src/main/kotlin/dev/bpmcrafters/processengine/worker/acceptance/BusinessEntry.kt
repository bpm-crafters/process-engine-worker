package dev.bpmcrafters.processengine.worker.acceptance

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "worker_business_entry_")
open class BusinessEntry(
  @field:Id
  @field:Column(name = "task_id_", length = 100)
  private var taskId: String,
  @field:Column(name = "value_", nullable = false, length = 100)
  private var value: String
)
