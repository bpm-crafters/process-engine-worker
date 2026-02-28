package dev.bpmcrafters.processengine.worker.itest.camunda7.external.application

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.springframework.data.repository.CrudRepository
import java.time.OffsetDateTime
import java.util.UUID

@Entity(name = "MyEntity")
@Table(name = "my_entity")
class MyEntity(

  @Id
  @Column(name = "id_")
  var id: String = UUID.randomUUID().toString(),

  /* Must be `@CreationTimestamp` and not `@Version`, because `@CreationTimestamp` is written on `EntityManager.flush()`
   * (which is on commit in our test cases), whereas `@Version` is written on `EntityManager.persist(Object)`.
   */
  @CreationTimestamp
  @Column(name = "created_at_")
  var createdAt: OffsetDateTime? = null,

  @Column(name = "name_", unique = true)
  var name: String,

  @Column(name = "verified")
  var verified: Boolean = false,
) {

  constructor() : this(name = "")

  override fun toString(): String {
    return "MyEntity(id='$id', createdAt='$createdAt', name='$name', verified=$verified)"
  }
}

interface MyEntityRepository : CrudRepository<MyEntity, String> {
  fun findByName(name: String): MyEntity?
}
