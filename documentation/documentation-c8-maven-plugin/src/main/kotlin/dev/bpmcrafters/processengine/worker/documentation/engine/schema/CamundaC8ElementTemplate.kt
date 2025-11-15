package dev.bpmcrafters.processengine.worker.documentation.engine.schema

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.fasterxml.jackson.annotation.JsonPropertyOrder

/**
 * Element Template Schema
 * An element template configuration
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(
  value = [
    "name",
    "id",
    "description",
    "version",
    "isDefault",
    "appliesTo",
    "elementType",
    "metadata",
    "entriesVisible",
    "groups",
    "documentationRef",
    "properties",
    "icon"
  ]
)
class CamundaC8ElementTemplate {
  /** The name of the element template (Required) */
  @JsonProperty("name")
  @JsonPropertyDescription("The name of the element template")
  var name: String? = null

  /** The identifier of the element template (Required) */
  @JsonProperty("id")
  @JsonPropertyDescription("The identifier of the element template")
  var id: String? = null

  /** The description of the element template */
  @JsonProperty("description")
  @JsonPropertyDescription("The description of the element template")
  var description: String? = null

  /** Optional version of the template. Unique by id+version */
  @JsonProperty("version")
  @JsonPropertyDescription(
    "Optional version of the template. If you add a version to a template it will be considered unique based on its ID and version. Two templates can have the same ID if their version is different"
  )
  var version: Int? = null

  /** Indicates whether the element template is a default template */
  @JsonProperty("isDefault")
  @JsonPropertyDescription("Indicates whether the element template is a default template")
  var isDefault: Boolean? = null

  /** List of BPMN types the template can be applied to (Required) */
  @JsonProperty("appliesTo")
  @JsonPropertyDescription("List of BPMN types the template can be applied to")
  var appliesTo: MutableList<String> = ArrayList()

  /** The BPMN type the element will be transformed into */
  @JsonProperty("elementType")
  @JsonPropertyDescription("The BPMN type the element will be transformed into")
  var elementType: ElementType? = null

  /** Some custom properties for further configuration */
  @JsonProperty("metadata")
  @JsonPropertyDescription("Some custom properties for further configuration")
  var metadata: Metadata? = null

  /** Select whether non-template entries are visible in the properties panel */
  @JsonProperty("entriesVisible")
  @JsonPropertyDescription("Select whether non-template entries are visible in the properties panel")
  var entriesVisible: Boolean? = null

  /** Custom fields can be ordered together via groups */
  @JsonProperty("groups")
  @JsonPropertyDescription("Custom fields can be ordered together via groups")
  var groups: MutableList<Group> = ArrayList()

  /** element template documentationRef */
  @JsonProperty("documentationRef")
  var documentationRef: String? = null

  /** List of properties of the element template (Required) */
  @JsonProperty("properties")
  @JsonPropertyDescription("List of properties of the element template")
  var properties: MutableList<Property> = ArrayList()

  /** Custom icon to be shown on the element */
  @JsonProperty("icon")
  @JsonPropertyDescription("Custom icon to be shown on the element")
  var icon: Icon? = null
}
