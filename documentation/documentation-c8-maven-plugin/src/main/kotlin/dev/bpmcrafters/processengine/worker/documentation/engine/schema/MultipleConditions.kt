package dev.bpmcrafters.processengine.worker.documentation.engine.schema

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

/**
 * property condition
 * Condition(s) to activate the binding
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(
  value = [
    "allMatch"
  ]
)
class MultipleConditions : Condition {
  /** condition allMatch (Required) */
  @JsonProperty("allMatch")
  var allMatch: MutableList<SingleCondition> = ArrayList()
}
