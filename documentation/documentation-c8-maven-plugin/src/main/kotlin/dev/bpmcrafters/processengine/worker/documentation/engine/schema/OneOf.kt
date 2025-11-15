package dev.bpmcrafters.processengine.worker.documentation.engine.schema

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

/**
 * condition oneOf
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(
  value = [
    "oneOf"
  ]
)
class OneOf : Expression {
  /** condition oneOf */
  @JsonProperty("oneOf")
  var oneOf: MutableList<Any?> = ArrayList()
}
