package dev.bpmcrafters.processengine.worker.acceptance

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Base64
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SerializationSamplesTest {
  @Test
  fun roundTripsDeepArrayContentsAndSharedReferenceIdentity() {
    ObjectInputStream(ByteArrayInputStream(historicalJavaGraph())).use { input ->
      assertThat(SerializationSamples.matches(input.readObject() as Map<*, *>)).isTrue()
    }
    ByteArrayOutputStream().use { bytes ->
      ObjectOutputStream(bytes).use { output ->
        output.writeObject(SerializationSamples.create())
        output.flush()
        ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { input ->
          assertThat(SerializationSamples.matches(input.readObject() as Map<*, *>)).isTrue()
        }
      }
    }
  }

  companion object {
    /** Frozen output of the pre-migration Java SerializationSamples and Java record DTOs. */
    fun historicalJavaGraph(): ByteArray = requireNotNull(
      SerializationSamplesTest::class.java.getResourceAsStream("/serialization/java-worker-graph.base64")
    ).use { Base64.getMimeDecoder().decode(it.readAllBytes()) }
  }

  @Test
  @Suppress("UNCHECKED_CAST")
  fun rejectsChangedPrimitiveContentsAndEqualButUnsharedReferences() {
    val wrongContent = SerializationSamples.create()
    (wrongContent["ints"] as IntArray)[0] = 999
    assertThat(SerializationSamples.matches(wrongContent)).isFalse()
    val wrongIdentity = SerializationSamples.create()
    (wrongIdentity["references"] as Array<Array<Any>>)[1][0] = ArrayList(wrongIdentity["shared"] as List<*>)
    assertThat(SerializationSamples.matches(wrongIdentity)).isFalse()
  }
}
