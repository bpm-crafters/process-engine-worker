package dev.bpmcrafters.example.camunda7remote.worker

import dev.bpmcrafters.example.camunda7remote.db.MyEntity
import dev.bpmcrafters.example.camunda7remote.db.MyEntityRepository
import org.springframework.stereotype.Service

@Service
class MyEntityService(private val repository: MyEntityRepository) {

  fun createEntity(name: String) = repository.save(MyEntity(name = name))

}
