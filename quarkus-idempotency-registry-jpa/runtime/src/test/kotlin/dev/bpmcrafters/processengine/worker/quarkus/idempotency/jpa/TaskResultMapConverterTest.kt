package dev.bpmcrafters.processengine.worker.quarkus.idempotency.jpa

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.NotSerializableException
import java.io.ObjectOutputStream
import java.io.Serializable
import java.math.BigDecimal
import java.nio.charset.StandardCharsets.ISO_8859_1
import java.time.Instant
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.LinkedList
import java.util.TreeMap
import java.util.TreeSet
import java.util.UUID

class TaskResultMapConverterTest {
  private val converter = TaskResultMapConverter()

  @Test
  fun savesNullAndEmptyAsSqlNullAndReadsSqlNullAsEmpty() {
    assertThat(converter.convertToDatabaseColumn(null)).isNull()
    assertThat(converter.convertToDatabaseColumn(java.util.Map.of())).isNull()
    assertThat(converter.convertToEntityAttribute(null)).isEmpty()
  }

  @Test
  fun preservesOriginalJavaSerializationBytesAndNullValues() {
    val original = linkedMapOf<String, Any?>("null" to null, "dto" to Payload("value", 7))
    val legacyBytes = ByteArrayOutputStream().use { bytes ->
      ObjectOutputStream(bytes).use { output ->
        output.writeObject(original)
        output.flush()
        bytes.toByteArray()
      }
    }
    assertThat(converter.convertToDatabaseColumn(original)).isEqualTo(legacyBytes)
    assertThat(converter.convertToEntityAttribute(legacyBytes)).isEqualTo(original)
  }

  @Test
  fun roundTripsNestedJavaAndKotlinContainersAndScalars() {
    // Keep Java immutable factories distinct from Kotlin collections and concrete mutable types.
    val containers = java.util.List.of(
      HashMap(java.util.Map.of("one", 1)), LinkedHashMap(java.util.Map.of("one", 1)),
      TreeMap(java.util.Map.of("one", 1)), ArrayList(java.util.List.of(1, 2)),
      LinkedList(java.util.List.of(1, 2)), Arrays.asList(1, 2),
      HashSet(java.util.Set.of(1, 2)), LinkedHashSet(java.util.Set.of(1, 2)), TreeSet(java.util.Set.of(1, 2)),
      java.util.Map.of<String, Int>(), java.util.Map.of("one", 1), java.util.Map.of("one", 1, "two", 2),
      java.util.List.of<Int>(), java.util.List.of(1), java.util.List.of(1, 2, 3),
      java.util.Set.of<Int>(), java.util.Set.of(1), java.util.Set.of(1, 2, 3),
      Collections.emptyMap<String, Int>(), Collections.emptyList<Int>(), Collections.emptySet<Int>(),
      Collections.singletonMap("one", 1), Collections.singletonList(1), Collections.singleton(1),
      Collections.unmodifiableMap(HashMap(java.util.Map.of("one", 1))),
      Collections.unmodifiableList(ArrayList(java.util.List.of(1, 2))),
      Collections.unmodifiableList(LinkedList(java.util.List.of(1, 2))),
      Collections.unmodifiableSet(HashSet(java.util.Set.of(1, 2))),
      emptyMap<String, Int>(), emptyList<Int>(), emptySet<Int>()
    )
    val result = java.util.Map.of(
      "containers", containers,
      "scalars", java.util.List.of("string", true, 1.toByte(), 2.toShort(), 3, 4L, 5F, 6.0, 'a',
        BigDecimal("123.45"), Instant.parse("2026-01-01T00:00:00Z"),
        UUID.fromString("00000000-0000-0000-0000-000000000001")),
      "dto", Payload("nested", 42)
    )
    assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(result))).isEqualTo(result)
  }

  @Test
  fun rejectsNonSerializableNestedValues() {
    assertThatThrownBy {
      converter.convertToDatabaseColumn(java.util.Map.of("nested", java.util.Map.of("invalid", Any())))
    }.isInstanceOf(IllegalArgumentException::class.java).hasMessage("Cannot serialize task result")
      .hasCauseInstanceOf(NotSerializableException::class.java)
  }

  @Test
  fun rejectsCorruptAndNonMapPayloads() {
    assertThatThrownBy { converter.convertToEntityAttribute(byteArrayOf(1, 2, 3)) }
      .isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("deserialize")
    ByteArrayOutputStream().use { bytes ->
      ObjectOutputStream(bytes).use { output ->
        output.writeObject("not a map")
        output.flush()
        assertThatThrownBy { converter.convertToEntityAttribute(bytes.toByteArray()) }
          .isInstanceOf(ClassCastException::class.java)
      }
    }
  }

  @Test
  fun reportsUnavailableHistoricalPayloadClasses() {
    val bytes = converter.convertToDatabaseColumn(java.util.Map.of("dto", Payload("old", 1)))!!
    // Preserve the stream's class-name length while simulating a removed DTO class.
    val unavailableClass = String(bytes, ISO_8859_1).replace("\$Payload", "\$Missing").toByteArray(ISO_8859_1)
    assertThatThrownBy { converter.convertToEntityAttribute(unavailableClass) }
      .isInstanceOf(IllegalArgumentException::class.java).hasCauseInstanceOf(ClassNotFoundException::class.java)
  }

  private data class Payload(val name: String, val count: Int) : Serializable
}
