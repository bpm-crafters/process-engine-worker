package dev.bpmcrafters.processengine.worker

/**
 * Exception for throwing a BPMN error.
 */
class BpmnErrorOccurred(
  /**
   * Message details.
   */
  override val message: String,
  /**
   * Error code.
   */
  val errorCode: String,
  /**
   * Payload passed to the process engine.
   */
  val payload: Map<String, Any> = mapOf()
) : RuntimeException(message)
