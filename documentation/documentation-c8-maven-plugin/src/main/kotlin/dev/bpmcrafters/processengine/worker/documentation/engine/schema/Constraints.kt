package dev.bpmcrafters.processengine.worker.documentation.engine.schema

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.fasterxml.jackson.annotation.JsonPropertyOrder

/**
 * property constraints
 * The validation constraints of a control field
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(
  value = [
    "notEmpty",
    "minLength",
    "maxLength",
    "pattern"
  ]
)
class Constraints {
  /** The control field must not be empty */
  @JsonProperty("notEmpty")
  @JsonPropertyDescription("The control field must not be empty")
  var notEmpty: Boolean? = null

  /** The minimal length of a control field value */
  @JsonProperty("minLength")
  @JsonPropertyDescription("The minimal length of a control field value")
  var minLength: Double? = null

  /** The maximal length of a control field value */
  @JsonProperty("maxLength")
  @JsonPropertyDescription("The maximal length of a control field value")
  var maxLength: Double? = null

  /** A regular expression pattern for a constraint */
  @JsonProperty("pattern")
  @JsonPropertyDescription("A regular expression pattern for a constraint")
  var pattern: String? = null
}
