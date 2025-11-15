package dev.bpmcrafters.processengine.worker.documentation.engine.schema

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

/**
 * element type
 * The BPMN type the element will be transformed into
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(
  value = [
    "value"
  ]
)
class ElementType {
  /**
   * element type value
   * The identifier of the element template
   */
  @JsonProperty("value")
  var value: String? = null
}
