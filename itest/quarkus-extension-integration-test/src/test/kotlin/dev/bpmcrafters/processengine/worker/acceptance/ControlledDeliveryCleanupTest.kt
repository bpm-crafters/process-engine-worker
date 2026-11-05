package dev.bpmcrafters.processengine.worker.acceptance

import java.util.concurrent.TimeoutException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ControlledDeliveryCleanupTest {
  @Test
  fun joinsEvenIfReleaseFailsAndSuppressesCleanupFailuresUnderTheOriginalAssertion() {
    val primary = AssertionError("Original database visibility assertion")
    val releaseFailure = IllegalStateException("Release endpoint unavailable")
    val joinFailure = TimeoutException("Delivery did not finish within its deadline")
    val events = arrayListOf<String>()
    val cleanup = AutoCloseable {
      ControlledDelivery.cleanup({
        events.add("release")
        throw releaseFailure
      }, {
        events.add("join")
        throw joinFailure
      })
    }
    assertThatThrownBy { cleanup.use { throw primary } }.isSameAs(primary)
    assertThat(events).containsExactly("release", "join")
    assertThat(primary.suppressed).hasSize(1)
    assertThat(primary.suppressed[0]).isInstanceOf(AssertionError::class.java).hasCause(releaseFailure)
    assertThat(releaseFailure.suppressed).containsExactly(joinFailure)
  }

  @Test
  fun releasesThenJoinsWhenCleanupSucceeds() {
    val events = arrayListOf<String>()
    ControlledDelivery.cleanup({ events.add("release") }, {
      events.add("join")
      null
    })
    assertThat(events).containsExactly("release", "join")
  }
}
