package dev.bpmcrafters.processengine.worker.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.Serializable

class TaskResultMapSerializerTest {

  private val converter = TaskResultMapConverter()

  @AfterEach
  fun restoreDefault() {
    TaskResultMapSerializer.DEFAULT = JavaTaskResultMapSerializer
  }

  data class Payload(val id: Int, val name: String) : Serializable

  @Test
  fun `java serializer round trip`() {
    val result = mapOf("amount" to 42L, "payload" to Payload(1, "x"), "list" to listOf(1, 2), "nothing" to null)

    val bytes = JavaTaskResultMapSerializer.serialize(result)

    assertThat(JavaTaskResultMapSerializer.deserialize(bytes)).isEqualTo(result)
  }

  @Test
  fun `jackson serializer round trip`() {
    val serializer = JacksonTaskResultMapSerializer(ObjectMapper())
    val result = mapOf("amount" to 42, "name" to "x", "list" to listOf(1, 2), "nested" to mapOf("k" to true), "nothing" to null)

    val bytes = serializer.serialize(result)

    assertThat(String(bytes)).startsWith("{")
    assertThat(serializer.deserialize(bytes)).isEqualTo(result)
  }

  @Test
  fun `converter maps empty and null to null column and back to empty map`() {
    assertThat(converter.convertToDatabaseColumn(mapOf())).isNull()
    assertThat(converter.convertToDatabaseColumn(null)).isNull()
    assertThat(converter.convertToEntityAttribute(null)).isEmpty()
  }

  @Test
  fun `converter uses java serialization by default`() {
    val result = mapOf("a" to 1)

    val bytes = converter.convertToDatabaseColumn(result)

    assertThat(bytes).isEqualTo(JavaTaskResultMapSerializer.serialize(result))
    assertThat(converter.convertToEntityAttribute(bytes)).isEqualTo(result)
  }

  @Test
  fun `converter uses switched default serializer`() {
    val serializer = JacksonTaskResultMapSerializer(ObjectMapper())
    TaskResultMapSerializer.DEFAULT = serializer
    val result = mapOf("a" to 1, "b" to "two")

    val bytes = converter.convertToDatabaseColumn(result)

    assertThat(bytes).isEqualTo(serializer.serialize(result))
    assertThat(converter.convertToEntityAttribute(bytes)).isEqualTo(result)
  }
}
