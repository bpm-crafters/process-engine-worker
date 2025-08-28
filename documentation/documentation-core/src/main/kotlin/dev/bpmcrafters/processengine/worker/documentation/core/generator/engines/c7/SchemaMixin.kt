package dev.bpmcrafters.processengine.worker.documentation.core.generator.engines.c7

import com.fasterxml.jackson.databind.annotation.JsonAppend

@JsonAppend(attrs = [JsonAppend.Attr(value = "\$schema")], prepend = true)
class SchemaMixin {
  // Because the generated element template does not have a property to
  // set the schema, we add it manually on serialization
}
