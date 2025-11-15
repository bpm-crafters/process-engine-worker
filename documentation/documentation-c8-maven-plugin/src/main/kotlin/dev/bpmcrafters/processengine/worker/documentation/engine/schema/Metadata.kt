package dev.bpmcrafters.processengine.worker.documentation.engine.schema

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import java.util.LinkedHashMap

/**
 * element template metadata
 *
 * Some custom properties for further configuration
 */
@JsonInclude(JsonInclude.Include.NON_NULL)

data class Metadata(
  @field:JsonIgnore
  var additionalProperties: MutableMap<String, Any?> = LinkedHashMap()
)
