package dev.bpmcrafters.processengine.worker.registrar

import tools.jackson.databind.ObjectMapper

/**
 * Helper converting variables from a JSON map to type using Jackson 3.
 * @since 0.9.0
 **/
open class Jackson3VariableConverter(
  private val objectMapper: ObjectMapper
) : VariableConverter {

  /**
   * Reads from the map-tree structure to a target type.
   * @param value map-tree structure.
   * @param type target class.
   * @return cast value.
   **/
  override fun <T : Any> mapToType(value: Any?, type: Class<T>): T {
    return if (value != null && !type.isInstance(value)) {
      objectMapper.readValue(objectMapper.writeValueAsString(value), type)
    } else {
      type.cast(value)
    }
  }
}
