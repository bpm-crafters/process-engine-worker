package dev.bpmcrafters.processengine.worker

import dev.bpmcrafters.processengine.worker.registrar.VariableConverter
import kotlin.reflect.KClass

/**
 * Indicates a typed process variable to be injected into the worker method.
 *
 * The attributes `name` and `value` are aliases of each other: specify either of them, but not both with different values.
 * @since 0.0.1
 */
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.ANNOTATION_CLASS)
@MustBeDocumented
annotation class Variable(
  /**
   * Name of the variable. Alias for `value`.
   */
  val name: String = DEFAULT_UNNAMED_NAME,
  /**
   * Name of the variable. Alias for `name`.
   */
  val value: String = DEFAULT_UNNAMED_NAME,
  /**
   * Indicates that a variable is mandatory. Defaults to `true`.
   */
  val mandatory: Boolean = true,
  /**
   * Specifies a converter class for this variable. The converter class must provide a public no-arg constructor.
   */
  val converter: KClass<out VariableConverter> = DefaultVariableConverter::class,
) {
  /**
   * Null object for converter, serves as a marker to use a globally defined converter.
   * @since 0.5.1
   */
  object DefaultVariableConverter : VariableConverter {
    override fun <T : Any> mapToType(value: Any?, type: Class<T>): T = throw NotImplementedError()
  }

  companion object {
    /**
     * Null value for the variable name.
     */
    const val DEFAULT_UNNAMED_NAME = "__unnamed"
  }
}
