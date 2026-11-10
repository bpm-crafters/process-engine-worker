package dev.bpmcrafters.processengine.worker.quarkus.idempotency.jpa

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/** Keeps the existing Spring JPA registry's Java serialization format. */
@Converter
class TaskResultMapConverter : AttributeConverter<Map<String, Any?>, ByteArray> {
  override fun convertToDatabaseColumn(attribute: Map<String, Any?>?): ByteArray? {
    if (attribute.isNullOrEmpty()) return null
    try {
      return ByteArrayOutputStream().use { bytes ->
        ObjectOutputStream(bytes).use { output ->
          output.writeObject(attribute)
          output.flush()
          bytes.toByteArray()
        }
      }
    } catch (exception: IOException) {
      throw IllegalArgumentException("Cannot serialize task result", exception)
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun convertToEntityAttribute(data: ByteArray?): Map<String, Any?> {
    if (data == null) return emptyMap()
    try {
      return ObjectInputStream(ByteArrayInputStream(data)).use { it.readObject() as Map<String, Any?> }
    } catch (exception: IOException) {
      throw IllegalArgumentException("Cannot deserialize stored task result", exception)
    } catch (exception: ClassNotFoundException) {
      throw IllegalArgumentException("Cannot deserialize stored task result", exception)
    }
  }
}
