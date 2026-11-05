package dev.bpmcrafters.processengine.worker.quarkus.idempotency.jpa.deployment

import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.NotSerializableException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.lang.reflect.Array as ReflectArray
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.util.Date
import java.util.UUID

class SerializationTypesTest {
  @Test
  fun registersSerializationRatherThanOnlyReflectionAndAllNamesExist() {
    val registration = JpaIdempotencyProcessor().serialization()
    assertThat(registration.isSerialization).isTrue()
    assertThat(registration.isConstructors).isTrue()
    for (name in registration.classNames) {
      assertThat(Class.forName(name)).isNotNull()
    }
  }

  @Test
  fun includesJvmDescriptorsForPrimitiveObjectAndScalarArraysInBothDimensions() {
    val metadata = JpaIdempotencyProcessor().serialization()
    val components = listOf(
      Boolean::class.javaPrimitiveType!!, Byte::class.javaPrimitiveType!!,
      Short::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
      Long::class.javaPrimitiveType!!, Float::class.javaPrimitiveType!!,
      Double::class.javaPrimitiveType!!, Char::class.javaPrimitiveType!!,
      Any::class.java, String::class.java, Boolean::class.javaObjectType,
      Byte::class.javaObjectType, Short::class.javaObjectType, Int::class.javaObjectType,
      Long::class.javaObjectType, Float::class.javaObjectType, Double::class.javaObjectType,
      Char::class.javaObjectType, Number::class.java, BigDecimal::class.java, BigInteger::class.java,
      UUID::class.java, Date::class.java, Instant::class.java, LocalDate::class.java
    )
    for (component in components) {
      for (dimensions in listOf(intArrayOf(0), intArrayOf(0, 0))) {
        val arrayClass = ReflectArray.newInstance(component, *dimensions).javaClass
        assertThat(metadata.classNames).contains(arrayClass.name)
        assertThat(Class.forName(arrayClass.name)).isEqualTo(arrayClass)
      }
    }
    assertThat(metadata.classNames).contains("[[I", "[Ljava.lang.Integer;", "[[Ljava.lang.Object;")
    assertThat(metadata.classNames).containsAll(ReflectiveClassBuildItem.builder(
      Array<IntArray>::class.java, Array<Int>::class.java, Array<Array<Any>>::class.java
    ).serialization().build().classNames)
  }

  @Test
  fun coversTheActualObjectOutputStreamAbortGraphIncludingArrays() {
    val serializedTypes = HashSet<String>()
    ByteArrayOutputStream().use { bytes ->
      RecordingOutput(bytes, serializedTypes).use { output ->
        assertThatThrownBy { output.writeObject(java.util.Map.of("nested", java.util.Map.of("invalid", Any()))) }
          .isInstanceOf(NotSerializableException::class.java).hasMessage("java.lang.Object")
        assertThat(serializedTypes).contains("java.lang.Throwable", "java.lang.Exception",
          "java.io.IOException", "java.io.ObjectStreamException", "java.io.NotSerializableException",
          "java.lang.StackTraceElement", "[Ljava.lang.StackTraceElement;", "java.util.Collections\$EmptyList")
        assertThat(JpaIdempotencyProcessor().serialization().classNames).containsAll(serializedTypes)
      }
    }
  }

  @Test
  fun coversTheStandardAbortExceptionGraphWithSuppressedIoExceptions() {
    val abort = NotSerializableException("invalid payload")
    abort.addSuppressed(IOException("suppressed I/O failure"))
    val serializedTypes = HashSet<String>()
    ByteArrayOutputStream().use { bytes ->
      RecordingOutput(bytes, serializedTypes).use { output ->
        output.writeObject(abort)
        output.flush()
        assertThat(serializedTypes).contains("java.util.ArrayList", "[Ljava.lang.StackTraceElement;")
        assertThat(JpaIdempotencyProcessor().serialization().classNames).containsAll(serializedTypes)
        ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { input ->
          assertThat(input.readObject() as NotSerializableException).hasMessage("invalid payload")
            .hasSuppressedException(IOException("suppressed I/O failure"))
        }
      }
    }
  }

  @Test
  fun includesWriteReplaceDelegatesAndRoundTripsBuiltKotlinCollections() {
    val map = buildMap<String, Any?> { put("one", 1) }
    val list = buildList<Any?> { add(2) }
    val set = buildSet<Any?> { add(3) }
    val original = java.util.Map.of(
      "built", java.util.List.of(map, list, set),
      "java", java.util.List.of(java.util.Map.of("two", 2), java.util.List.of(3), java.util.Set.of(4)),
      "scalars", java.util.List.of(Instant.parse("2026-01-01T00:00:00Z"), BigDecimal("12.34"))
    )
    val serializedTypes = HashSet<String>()
    ByteArrayOutputStream().use { bytes ->
      RecordingOutput(bytes, serializedTypes).use { output ->
        output.writeObject(original)
        output.flush()
        assertThat(serializedTypes).contains("java.util.CollSer", "java.time.Ser",
          "kotlin.collections.builders.SerializedMap", "kotlin.collections.builders.SerializedCollection")
        assertThat(SerializationTypes.names().toSet()).containsAll(serializedTypes)
        ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { input ->
          assertThat(input.readObject()).isEqualTo(original)
        }
      }
    }
  }

  private class RecordingOutput(bytes: ByteArrayOutputStream, private val types: MutableSet<String>) :
    ObjectOutputStream(bytes) {
    override fun annotateClass(type: Class<*>) {
      types.add(type.name)
    }
  }
}
