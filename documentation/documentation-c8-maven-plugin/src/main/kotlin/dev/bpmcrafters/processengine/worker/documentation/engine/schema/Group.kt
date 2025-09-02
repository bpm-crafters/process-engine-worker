package dev.bpmcrafters.processengine.worker.documentation.engine.schema

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.fasterxml.jackson.annotation.JsonPropertyOrder

/**
 * element template groups
 * Custom fields can be ordered together via groups
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(
  value = [
    "id",
    "label"
  ]
)
class Group {
  /** The id of the custom group (Required) */
  @JsonProperty("id")
  @JsonPropertyDescription("The id of the custom group")
  var id: String? = null

  /** The label of the custom group (Required) */
  @JsonProperty("label")
  @JsonPropertyDescription("The label of the custom group")
  var label: String? = null
}
