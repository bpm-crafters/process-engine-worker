package dev.bpmcrafters.processengine.worker.documentation.core

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper

class SchemaJsonConverter {

  companion object {
    fun <T: Any> toJsonString(jsonSchema: String, content: T, contentClass: Class<T>): String {
      try {
        val jsonSchema = "https://unpkg.com/@camunda/element-templates-json-schema@0.1.0/resources/schema.json"
        val mapper = JsonMapper.builder()
          .configure(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST, true)
          .addMixIn(contentClass, SchemaMixin::class.java)
          .build()
        val objectWriter = mapper.writerFor(contentClass)
          .withAttribute("\$schema", jsonSchema)
        return objectWriter.withDefaultPrettyPrinter().writeValueAsString(content)
      } catch (e: JsonProcessingException) {
        throw RuntimeException("Could not generate json string!", e)
      }
    }
  }

}
