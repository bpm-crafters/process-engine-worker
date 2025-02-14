package dev.bpmcrafters.example.camunda7remote.db

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.*

@Entity(name = "MyEntity")
@Table(name = "my_entity")
class MyEntity(

  @Id
  @Column(name = "id_")
  var id: String = UUID.randomUUID().toString(),

  @Column(name = "name_", unique = true)
  var name: String,
) {

  constructor() : this(name = "")
}
