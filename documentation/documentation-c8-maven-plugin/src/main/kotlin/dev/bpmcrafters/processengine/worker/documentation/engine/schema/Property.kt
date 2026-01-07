package dev.bpmcrafters.processengine.worker.documentation.engine.schema

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.fasterxml.jackson.annotation.JsonPropertyOrder

/**
 * element template property
 * List of properties of the element template
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(
  value = [
    "id",
    "value",
    "description",
    "label",
    "type",
    "editable",
    "choices",
    "constraints",
    "group",
    "condition",
    "binding",
    "optional",
    "feel",
    "language"
  ]
)
class Property {
  /** Unique identifier of the property */
  @JsonProperty("id")
  @JsonPropertyDescription("Unique identifier of the property")
  var id: String? = null

  /** The value of a control field */
  @JsonProperty("value")
  @JsonPropertyDescription("The value of a control field")
  var value: Any? = null

  /** The description of a control field */
  @JsonProperty("description")
  @JsonPropertyDescription("The description of a control field")
  var description: String? = null

  /** The label of a control field */
  @JsonProperty("label")
  @JsonPropertyDescription("The label of a control field")
  var label: String? = null

  /** The type of a control field */
  @JsonProperty("type")
  @JsonPropertyDescription("The type of a control field")
  var type: String? = null

  /** Indicates whether a control field is editable or not */
  @JsonProperty("editable")
  @JsonPropertyDescription("Indicates whether a control field is editable or not")
  var editable: Boolean? = null

  /** The choices for dropdown fields */
  @JsonProperty("choices")
  @JsonPropertyDescription("The choices for dropdown fields")
  var choices: MutableList<Choice> = ArrayList()

  /** The validation constraints of a control field */
  @JsonProperty("constraints")
  @JsonPropertyDescription("The validation constraints of a control field")
  var constraints: Constraints? = null

  /** The custom group of a control field */
  @JsonProperty("group")
  @JsonPropertyDescription("The custom group of a control field")
  var group: String? = null

  /** Condition(s) to activate the binding */
  @JsonProperty("condition")
  @JsonPropertyDescription("Condition(s) to activate the binding")
  var condition: Condition? = null

  /** Specifying how the property is mapped to BPMN or Zeebe extension elements and attributes (Required) */
  @JsonProperty("binding")
  @JsonPropertyDescription("Specifying how the property is mapped to BPMN or Zeebe extension elements and attributes")
  var binding: Binding? = null

  /** Indicates whether a property is optional. Optional bindings do not persist empty values in the underlying BPMN 2.0 XML */
  @JsonProperty("optional")
  @JsonPropertyDescription("Indicates whether a property is optional. Optional bindings do not persist empty values in the underlying BPMN 2.0 XML")
  var optional: Boolean? = null

  /** Indicates whether the property can be a feel expression */
  @JsonProperty("feel")
  @JsonPropertyDescription("Indicates whether the property can be a feel expression")
  var feel: String? = null

  /** Indicates that the field is a custom language editor */
  @JsonProperty("language")
  @JsonPropertyDescription("Indicates that the field is a custom language editor")
  var language: String? = null
}
