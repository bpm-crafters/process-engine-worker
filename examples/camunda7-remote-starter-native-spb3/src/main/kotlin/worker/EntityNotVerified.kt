package dev.bpmcrafters.example.camunda7remote.worker

import dev.bpmcrafters.example.camunda7remote.db.MyEntity

class EntityNotVerified(val entity: MyEntity) : RuntimeException("Entity $entity not verified")
