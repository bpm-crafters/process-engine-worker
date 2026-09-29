package dev.bpmcrafters.processengine.worker.idempotency

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Serializer based on Jackson JSON serialization. Suitable for native images, values are deserialized as JSON tree types
 * (maps, lists, strings, numbers, booleans).
 * @param objectMapper object mapper to use.
 * @since 0.8.6
 */
class JacksonTaskResultMapSerializer(
  private val objectMapper: ObjectMapper
) : TaskResultMapSerializer {

  private val mapType = object : TypeReference<Map<String, Any?>>() {}

  override fun serialize(result: Map<String, Any?>): ByteArray = objectMapper.writeValueAsBytes(result)

  override fun deserialize(bytes: ByteArray): Map<String, Any?> = objectMapper.readValue(bytes, mapType)
}
