package dev.bpmcrafters.processengine.worker

class BpmnErrorOccurred(
  override val message: String,
  val errorCode: String,
  val payload: Map<String, Any> = mapOf()
) : RuntimeException(message)
