package dev.bpmcrafters.processengine.worker.idempotency

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

@Converter(autoApply = false)
internal class MapConverter : AttributeConverter<Map<String, Any?>, ByteArray> {

  override fun convertToDatabaseColumn(attribute: Map<String, Any?>?): ByteArray? {
    if (attribute == null) {
      return null
    }
    ByteArrayOutputStream().use {
      ObjectOutputStream(it).writeObject(attribute)
      return it.toByteArray()
    }
  }

  override fun convertToEntityAttribute(dbData: ByteArray?): Map<String, Any?>? {
    if (dbData == null) {
      return null
    }
    @Suppress("UNCHECKED_CAST")
    return ObjectInputStream(ByteArrayInputStream(dbData)).readObject() as Map<String, Any?>
  }
}
