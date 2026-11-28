package dev.bpmcrafters.example.camunda7remote.db

import org.springframework.data.repository.CrudRepository

interface MyEntityRepository : CrudRepository<MyEntity, String>
