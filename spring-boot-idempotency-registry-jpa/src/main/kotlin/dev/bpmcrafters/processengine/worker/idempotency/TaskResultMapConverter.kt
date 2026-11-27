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
 * @since 0.8.1
 **/
@Converter(autoApply = false)
internal class TaskResultMapConverter : AttributeConverter<Map<String, Any?>, ByteArray> {

  /**
   * Serializes a result map to bytes and stores empty results as SQL null.
   **/
  override fun convertToDatabaseColumn(attribute: Map<String, Any?>?): ByteArray? {
    if (attribute.isNullOrEmpty()) {
      return null
    }
    ByteArrayOutputStream().use {
      ObjectOutputStream(it).writeObject(attribute)
      return it.toByteArray()
    }
  }

  /**
   * Deserializes a result map and treats SQL null as an empty result.
   **/
  override fun convertToEntityAttribute(dbData: ByteArray?): Map<String, Any?> {
    if (dbData == null) {
      return mapOf()
    }
    @Suppress("UNCHECKED_CAST")
    return ObjectInputStream(ByteArrayInputStream(dbData)).readObject() as Map<String, Any?>
  }

}
