package dev.bpmcrafters.processengine.worker.itest.camunda7.external.application

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

  fun findByName(name: String): MyEntity? {
    return repository.findByName(name)
  }
}
