package dev.bpmcrafters.processengine.worker

/**
 * Indicates a typed process variable to be injected into the worker.
 */
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.ANNOTATION_CLASS)
@MustBeDocumented
annotation class Variable(
  /**
   * Name of the variable.
   */
  val name: String,
  /**
   * Indicates that a variable is mandatory.
   */
  val mandatory: Boolean = true
)
