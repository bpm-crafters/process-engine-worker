package dev.bpmcrafters.processengine.worker.registrar

import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Helper converting variables from a JSON map to type using Jackson.
 * @since 0.0.3
 */
open class JacksonVariableConverter(
  private val objectMapper: ObjectMapper
) : VariableConverter {

  /**
   * Reads from the map-tree structure to a target type.
   * @param value map-tree structure.
   * @param type target class.
   * @return cast value.
   */
  override fun <T : Any> mapToType(value: Any?, type: Class<T>): T {
    return if (value != null && !type.isInstance(value)) {
      objectMapper.readValue(objectMapper.writeValueAsString(value), type)
    } else {
      type.cast(value)
    }
  }
}
