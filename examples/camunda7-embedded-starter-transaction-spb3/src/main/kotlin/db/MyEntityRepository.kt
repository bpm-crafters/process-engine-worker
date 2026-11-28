package dev.bpmcrafters.example.camunda7embedded.db

import org.springframework.data.repository.CrudRepository

interface MyEntityRepository : CrudRepository<MyEntity, String>
