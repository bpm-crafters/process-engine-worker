package dev.bpmcrafters.processengine.worker

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BpmnErrorOccurredTest {

  @Test
  fun `should keep the passed cause`() {
    val cause = IllegalStateException("root cause")

    val bpmnError = BpmnErrorOccurred(message = "failed", errorCode = "SOME_ERROR", cause = cause)

    assertThat(bpmnError.cause).isSameAs(cause)
    assertThat(bpmnError.message).isEqualTo("failed")
    assertThat(bpmnError.errorCode).isEqualTo("SOME_ERROR")
    assertThat(bpmnError.payload).isEmpty()
  }

  @Test
  fun `should have no cause by default`() {
    val bpmnError = BpmnErrorOccurred(message = "failed", errorCode = "SOME_ERROR")

    assertThat(bpmnError.cause).isNull()
  }

  @Test
  fun `should retain the original Kotlin default payload constructor ABI`() {
    val constructor = BpmnErrorOccurred::class.java.getConstructor(
      String::class.java, String::class.java, Map::class.java, Integer.TYPE,
      Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")
    )

    // Already compiled Kotlin callers use bit 4 to default the third parameter, payload.
    val bpmnError = constructor.newInstance("failed", "SOME_ERROR", null, 4, null)

    assertThat(bpmnError.message).isEqualTo("failed")
    assertThat(bpmnError.errorCode).isEqualTo("SOME_ERROR")
    assertThat(bpmnError.payload).isEmpty()
    assertThat(bpmnError.cause).isNull()
  }

  @Test
  fun `should allow Java three argument callers to initialize the cause later`() {
    val payload = mapOf<String, Any>("reason" to "rejected")
    val constructor = BpmnErrorOccurred::class.java.getConstructor(
      String::class.java, String::class.java, Map::class.java
    )
    val bpmnError = constructor.newInstance("failed", "SOME_ERROR", payload)
    val cause = IllegalStateException("root cause")

    bpmnError.initCause(cause)

    assertThat(bpmnError.cause).isSameAs(cause)
    assertThat(bpmnError.message).isEqualTo("failed")
    assertThat(bpmnError.errorCode).isEqualTo("SOME_ERROR")
    assertThat(bpmnError.payload).isEqualTo(payload)
  }

  @Test
  fun `should keep an explicit cause with a supplied payload`() {
    val cause = IllegalStateException("root cause")
    val payload = mapOf<String, Any>("reason" to "rejected")

    val bpmnError = BpmnErrorOccurred("failed", "SOME_ERROR", payload, cause)

    assertThat(bpmnError.cause).isSameAs(cause)
    assertThat(bpmnError.message).isEqualTo("failed")
    assertThat(bpmnError.errorCode).isEqualTo("SOME_ERROR")
    assertThat(bpmnError.payload).isEqualTo(payload)
  }

  @Test
  fun `should initialize an explicit cause without calling a subclass override`() {
    val cause = IllegalStateException("root cause")
    val bpmnError = object : BpmnErrorOccurred("failed", "SOME_ERROR", cause = cause) {
      override fun initCause(cause: Throwable?): Throwable {
        throw AssertionError("Constructor must not call a subclass override")
      }
    }

    assertThat(bpmnError.cause).isSameAs(cause)
  }

  @Test
  fun `should accept an explicit null cause with default payload`() {
    val bpmnError = BpmnErrorOccurred("failed", "SOME_ERROR", cause = null)

    assertThat(bpmnError.cause).isNull()
    assertThat(bpmnError.message).isEqualTo("failed")
    assertThat(bpmnError.errorCode).isEqualTo("SOME_ERROR")
    assertThat(bpmnError.payload).isEmpty()
  }

  @Test
  fun `should accept an explicit null cause with supplied payload`() {
    val payload = mapOf<String, Any>("reason" to "rejected")

    val bpmnError = BpmnErrorOccurred("failed", "SOME_ERROR", payload, null)

    assertThat(bpmnError.cause).isNull()
    assertThat(bpmnError.message).isEqualTo("failed")
    assertThat(bpmnError.errorCode).isEqualTo("SOME_ERROR")
    assertThat(bpmnError.payload).isEqualTo(payload)
  }
}
