package dev.bpmcrafters.processengine.worker.registrar

/**
 * Interface for variable converter.
 * @since 0.6.0
 */
@FunctionalInterface
interface VariableConverter {
  /**
   * Converts from a payload value representation to an instance of a target type.
   * @param value value structure.
   * @param type target class.
   * @return cast value.
   */
  fun <T : Any> mapToType(value: Any?, type: Class<T>): T
}
