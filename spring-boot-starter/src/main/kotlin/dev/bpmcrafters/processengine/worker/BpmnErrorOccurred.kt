package dev.bpmcrafters.processengine.worker

/**
 * Exception for throwing a BPMN error.
 * As an implementer of the process engine worker you can either throw this exception
 * directly from your worker or subclass it with your business exception.
 * @since 0.0.1
 */
open class BpmnErrorOccurred(
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
) : Exception(message)

