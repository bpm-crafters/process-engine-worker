package dev.bpmcrafters.processengine.worker

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BpmnErrorOccurredTest {

  @Test
  fun `should keep the passed cause`() {
    val cause = IllegalStateException("root cause")

    val bpmnError = BpmnErrorOccurred(message = "failed", errorCode = "SOME_ERROR", cause = cause)

    assertThat(bpmnError.cause).isSameAs(cause)
  }

  @Test
  fun `should have no cause by default`() {
    val bpmnError = BpmnErrorOccurred(message = "failed", errorCode = "SOME_ERROR")

    assertThat(bpmnError.cause).isNull()
  }
}
