package dev.bpmcrafters.processengine.worker.documentation.engine.schema

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.fasterxml.jackson.annotation.JsonPropertyOrder

/**
 * property choice
 * The choices for dropdown fields
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(
  value = [
    "name",
    "value"
  ]
)
class Choice {
  /** The name of a choice (Required) */
  @JsonProperty("name")
  @JsonPropertyDescription("The name of a choice")
  var name: String? = null

  /** The value of a choice (Required) */
  @JsonProperty("value")
  @JsonPropertyDescription("The value of a choice")
  var value: String? = null
}
