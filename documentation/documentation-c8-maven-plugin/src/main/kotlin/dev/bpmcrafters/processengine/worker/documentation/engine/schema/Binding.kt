package dev.bpmcrafters.processengine.worker.documentation.engine.schema

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.annotation.JsonValue

/**
 * property binding
 * Specifying how the property is mapped to BPMN or Zeebe extension elements and attributes
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(
  value = [
    "type",
    "name",
    "source",
    "key"
  ]
)
class Binding {
  /**
   * The type of a property binding (Required)
   */
  @JsonProperty("type")
  @JsonPropertyDescription("The type of a property binding")
  var type: Type? = null

  /**
   * The name of a property binding
   */
  @JsonProperty("name")
  @JsonPropertyDescription("The name of a property binding")
  var name: String? = null

  /**
   * The source value of a property binding (zeebe:output)
   */
  @JsonProperty("source")
  @JsonPropertyDescription("The source value of a property binding (zeebe:output)")
  var source: String? = null

  /**
   * The key value of a property binding (zeebe:taskHeader)
   */
  @JsonProperty("key")
  @JsonPropertyDescription("The key value of a property binding (zeebe:taskHeader)")
  var key: String? = null

  /**
   * property binding type
   */
  enum class Type(@get:JsonValue val value: String) {
    PROPERTY("property"),
    ZEEBE_TASKDEFINITION_TYPE("zeebe:taskDefinition:type"),
    ZEEBE_TASKDEFINITION_RETRIES("zeebe:taskDefinition:retries"),
    ZEEBE_INPUT("zeebe:input"),
    ZEEBE_OUTPUT("zeebe:output"),
    ZEEBE_PROPERTY("zeebe:property"),
    ZEEBE_TASKHEADER("zeebe:taskHeader");

    companion object {
      @JvmStatic
      @JsonCreator
      fun fromValue(value: String): Type = values().find { it.value == value }
        ?: throw IllegalArgumentException(value)
    }
  }
}
