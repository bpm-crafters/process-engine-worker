package dev.bpmcrafters.processengine.worker.quarkus

import io.quarkus.test.QuarkusExtensionTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class RepeatedShutdownFailureTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot {
      it.addClasses(
        RepeatedShutdownTest.State::class.java, RepeatedShutdownTest.Workers::class.java,
        RepeatedShutdownTest.Engine::class.java, RepeatedShutdownTest.Handle::class.java,
        RepeatedShutdownTest.Metadata::class.java
      )
    }
  }

  @Inject
  lateinit var state: RepeatedShutdownTest.State

  @Test
  fun allExternalShutdownCallersObserveAsyncUnsubscribeFailureAndCleanupRunsOnce() {
    val executor = Executors.newFixedThreadPool(3)
    val failure = IllegalStateException("unsubscribe failed")
    try {
      val delivery = executor.submit { state.deliver("accepted") }
      state.workerStopReturned.get(5, TimeUnit.SECONDS)
      state.unsubscribeStarted.get(5, TimeUnit.SECONDS)
      val started = CountDownLatch(2)
      val shutdowns = (1..2).map {
        executor.submit {
          started.countDown()
          state.shutdownRuntime()
        }
      }
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()
      assertAll(
        { assertThatThrownBy { shutdowns[0].get(150, TimeUnit.MILLISECONDS) }.isInstanceOf(TimeoutException::class.java) },
        { assertThatThrownBy { shutdowns[1].get(150, TimeUnit.MILLISECONDS) }.isInstanceOf(TimeoutException::class.java) }
      )
      state.releaseWorker.complete(null)
      delivery.get(5, TimeUnit.SECONDS)
      assertThat(state.disposed).hasValue(0)
      state.unsubscribeResult.completeExceptionally(failure)
      shutdowns.forEach { shutdown ->
        assertThatThrownBy { shutdown.get(5, TimeUnit.SECONDS) }
          .isInstanceOf(ExecutionException::class.java)
          .hasRootCause(failure)
      }
      state.allDisposed.get(5, TimeUnit.SECONDS)
      assertThatThrownBy { state.shutdownRuntime() }.hasRootCause(failure)
      assertThat(state.disposed).hasValue(2)
      assertThat(state.unsubscribes).hasValue(1)
      assertThat(state.closed).hasValue(0)
      assertThat(state.completed).containsExactly("accepted")
      assertThat(state.failed).isEmpty()
    } finally {
      state.releaseWorker.complete(null)
      state.unsubscribeResult.completeExceptionally(failure)
      executor.shutdownNow()
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
    }
  }
}
