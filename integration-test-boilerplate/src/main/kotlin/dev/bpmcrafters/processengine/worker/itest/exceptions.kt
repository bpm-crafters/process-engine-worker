package dev.bpmcrafters.processengine.worker.itest

import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred

class EntityNotVerified(val entity: MyEntity) : BpmnErrorOccurred(
  message = "Entity $entity not verified",
  errorCode = "ERROR"
)
