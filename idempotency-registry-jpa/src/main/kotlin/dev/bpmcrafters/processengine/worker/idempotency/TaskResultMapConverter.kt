package dev.bpmcrafters.processengine.worker.idempotency

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * This converter converts a result map to a byte array (and back) using the [TaskResultMapSerializer.DEFAULT] serializer
 * (Java serialization, unless replaced by the framework integration).
 *
 * It is important to note that empty results are converted to `null` to save space.
 */
@Converter(autoApply = false)
class TaskResultMapConverter : AttributeConverter<Map<String, Any?>, ByteArray> {

  override fun convertToDatabaseColumn(attribute: Map<String, Any?>?): ByteArray? {
    if (attribute.isNullOrEmpty()) {
      return null
    }
    return TaskResultMapSerializer.DEFAULT.serialize(attribute)
  }

  override fun convertToEntityAttribute(dbData: ByteArray?): Map<String, Any?> {
    if (dbData == null) {
      return mapOf()
    }
    return TaskResultMapSerializer.DEFAULT.deserialize(dbData)
  }

}
