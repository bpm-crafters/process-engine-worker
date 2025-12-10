package dev.bpmcrafters.processengine.worker.documentation.engine.schema

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.annotation.JsonUnwrapped

/**
 * property condition
 * Condition(s) to activate the binding
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(
  value = [
    "type",
    "property",
    "expression",
  ]
)
class SingleCondition : Condition {
  /** The type of the condition */
  @JsonProperty("type")
  @JsonPropertyDescription("The type of the condition")
  val type: String = "simple"

  /** The id of the property to check (Required) */
  @JsonProperty("property")
  @JsonPropertyDescription("The id of the property to check")
  var property: String? = null

  /** condition expression */
  @JsonUnwrapped
  var expression: Expression? = null
}
