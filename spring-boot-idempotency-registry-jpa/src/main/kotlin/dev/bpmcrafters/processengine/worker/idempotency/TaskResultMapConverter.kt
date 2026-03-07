package dev.bpmcrafters.processengine.worker.idempotency

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * This converter converts a result map to a byte array (and back) using Java serialization.
 *
 * It is important to not that empty results are converted to `null` to save space.
 */
@Converter(autoApply = false)
internal class TaskResultMapConverter : AttributeConverter<Map<String, Any?>, ByteArray> {

  override fun convertToDatabaseColumn(attribute: Map<String, Any?>?): ByteArray? {
    if (attribute.isNullOrEmpty()) {
      return null
    }
    ByteArrayOutputStream().use {
      ObjectOutputStream(it).writeObject(attribute)
      return it.toByteArray()
    }
  }

  override fun convertToEntityAttribute(dbData: ByteArray?): Map<String, Any?> {
    if (dbData == null) {
      return mapOf()
    }
    @Suppress("UNCHECKED_CAST")
    return ObjectInputStream(ByteArrayInputStream(dbData)).readObject() as Map<String, Any?>
  }

}
