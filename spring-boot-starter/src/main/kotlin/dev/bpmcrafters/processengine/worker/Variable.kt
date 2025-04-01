package dev.bpmcrafters.processengine.worker

/**
 * Indicates a typed process variable to be injected into the worker method.
 * @since 0.0.1
 */
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.ANNOTATION_CLASS)
@MustBeDocumented
annotation class Variable(
  /**
   * Name of the variable.
   */
  val name: String,
  /**
   * Indicates that a variable is mandatory. Defaults to `true`.
   */
  val mandatory: Boolean = true
)
