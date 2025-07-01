package dev.bpmcrafters.example.camunda7embedded.db

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Component
class DbService(
  val repository: MyEntityRepository
) {

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun save(e: MyEntity) {
    repository.save(e)
  }

  @Transactional(readOnly = true)
  fun findById(id: String): Optional<MyEntity> {
    return repository.findById(id)
  }
}
