package dev.bpmcrafters.processengine.worker.idempotency

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * Serializes task result maps for storage in the task log.
 * @since 0.8.6
 */
interface TaskResultMapSerializer {

  /**
   * Serializes a non-empty result map.
   * @param result result map.
   * @return serialized bytes.
   */
  fun serialize(result: Map<String, Any?>): ByteArray

  /**
   * Deserializes a result map.
   * @param bytes serialized bytes.
   * @return result map.
   */
  fun deserialize(bytes: ByteArray): Map<String, Any?>

  companion object {
    /**
     * Serializer used by the [TaskResultMapConverter]. Defaults to Java serialization and can be replaced by the
     * framework integration (e.g. with a JSON based serializer for native images). Must be set before the first entity is persisted.
     */
    @JvmStatic
    @Volatile
    var DEFAULT: TaskResultMapSerializer = JavaTaskResultMapSerializer
  }
}

/**
 * Serializer based on Java serialization. All values of the result map must be serializable.
 * @since 0.8.6
 */
object JavaTaskResultMapSerializer : TaskResultMapSerializer {

  override fun serialize(result: Map<String, Any?>): ByteArray {
    ByteArrayOutputStream().use {
      ObjectOutputStream(it).writeObject(result)
      return it.toByteArray()
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun deserialize(bytes: ByteArray): Map<String, Any?> =
    ObjectInputStream(ByteArrayInputStream(bytes)).readObject() as Map<String, Any?>
}
