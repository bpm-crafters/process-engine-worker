package dev.bpmcrafters.example.camunda7remote.worker

import dev.bpmcrafters.example.camunda7remote.db.MyEntity
import dev.bpmcrafters.example.camunda7remote.db.MyEntityRepository
import org.springframework.stereotype.Service

@Service
class MyEntityService(private val repository: MyEntityRepository) {

  fun verify(id: String): MyEntity {
    val entity = repository.findById(id).orElseThrow().apply {
      verified = true
    }

    return repository.save(entity)
  }

  @Throws(EntityNotVerified::class)
  fun createEntity(name: String, verified: Boolean): MyEntity {
    val entity = repository.save(MyEntity(name = name, verified = verified))

    if (!verified) {
      throw EntityNotVerified(entity)
    }

    return entity
  }

}
