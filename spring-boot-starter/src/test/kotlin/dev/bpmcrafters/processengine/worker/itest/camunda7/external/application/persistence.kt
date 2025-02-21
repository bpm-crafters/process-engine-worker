package dev.bpmcrafters.processengine.worker.itest.camunda7.external.application

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.repository.CrudRepository
import java.util.UUID


@Entity(name = "MyEntity")
@Table(name = "my_entity")
class MyEntity(

  @Id
  @Column(name = "id_")
  var id: String = UUID.randomUUID().toString(),

  @Column(name = "name_", unique = true)
  var name: String,

  @Column(name = "verified")
  var verified: Boolean = false,
) {

  constructor() : this(name = "")

  override fun toString(): String {
    return "MyEntity(id='$id', name='$name', verified=$verified)"
  }
}

interface MyEntityRepository : CrudRepository<MyEntity, String>
