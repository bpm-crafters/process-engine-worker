package dev.bpmcrafters.processengine.worker.registrar

import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Helper converting variables from a JSON map to type using Jackson.
 */
open class VariableConverter(
  private val objectMapper: ObjectMapper
) {

  open fun <T : Any> mapToType(value: Any?, type: Class<T>): T {
    return if (value != null && !type.isInstance(value)) {
      objectMapper.readValue(objectMapper.writeValueAsString(value), type)
    } else {
      type.cast(value)
    }
  }
}
