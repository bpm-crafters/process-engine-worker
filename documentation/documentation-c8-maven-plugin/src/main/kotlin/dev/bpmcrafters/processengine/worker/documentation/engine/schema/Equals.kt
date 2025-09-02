package dev.bpmcrafters.processengine.worker.documentation.engine.schema

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

/**
 * condition equals
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(
  value = [
    "equals"
  ]
)
class Equals : Expression {
  /** condition equals */
  @JsonProperty("equals")
  var equals: Any? = null
}
