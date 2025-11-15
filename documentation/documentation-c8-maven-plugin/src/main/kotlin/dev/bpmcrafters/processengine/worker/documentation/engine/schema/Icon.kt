package dev.bpmcrafters.processengine.worker.documentation.engine.schema

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.fasterxml.jackson.annotation.JsonPropertyOrder

/**
 * element template icon
 * Custom icon to be shown on the element
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(
  value = [
    "contents"
  ]
)
class Icon {
  /** The URL of an icon (Required) */
  @JsonProperty("contents")
  @JsonPropertyDescription("The URL of an icon")
  var contents: String? = null
}
