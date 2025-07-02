package dev.bpmcrafters.example.camunda7embedded.db

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity(name = "MyEntity")
@Table(name = "my_entity")
class MyEntity(
  @Id
  @Column(name = "process_instance_id_")
  var processInstanceId: String,

  @Column(name = "task_id_", unique = true)
  var taskId: String
) {
  constructor() : this(processInstanceId = "", taskId = "")

  override fun toString(): String {
    return "MyEntity(processInstanceId='$processInstanceId', taskId='$taskId')"
  }
}
