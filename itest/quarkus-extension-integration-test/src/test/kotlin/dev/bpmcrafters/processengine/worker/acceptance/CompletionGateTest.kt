package dev.bpmcrafters.processengine.worker.acceptance

import java.util.concurrent.CompletionException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CompletionGateTest {
  @Test
  fun cancellationBeforeRegistrationUnblocksALaterCompletion() {
    val gate = CompletionGate()
    gate.release(false)
    assertThatThrownBy { gate.register().join() }.isInstanceOf(CompletionException::class.java)
      .hasCauseInstanceOf(IllegalStateException::class.java)
    assertThat(gate.isPending()).isFalse()
  }

  @Test
  fun successBeforeRegistrationIsDurableAndRepeatedCleanupCannotOverrideIt() {
    val gate = CompletionGate()
    gate.release(true)
    gate.release(false)
    assertThat(gate.register()).isCompleted()
    assertThat(gate.isPending()).isFalse()
  }

  @Test
  fun releaseAfterRegistrationCompletesTheSameFutureAndIsIdempotent() {
    val gate = CompletionGate()
    val future = gate.register()
    assertThat(gate.isPending()).isTrue()
    gate.release(false)
    gate.release(true)
    assertThat(future).isCompletedExceptionally()
    assertThat(gate.register()).isSameAs(future)
    assertThat(gate.isPending()).isFalse()
  }
}
