package dev.bpmcrafters.processengine.worker.quarkus

import io.quarkus.runtime.BlockingOperationControl
import io.quarkus.runtime.IOThreadDetector
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DeliveryLifecycleTest {
  @BeforeEach
  fun initializePlainJvmThreadDetection() {
    BlockingOperationControl.setIoThreadDetector(emptyArray())
  }

  @Test
  fun shutdownDrainsAnActiveDeliveryWithoutBlockingTheCaller() {
    val executor = Executors.newSingleThreadExecutor()
    val entered = CompletableFuture<Void>()
    val release = CompletableFuture<Void>()
    val contexts = AtomicInteger()
    val lifecycle = DeliveryLifecycle(executor) { action -> contexts.incrementAndGet(); action.run() }
    try {
      val running = executor.submit { lifecycle.deliver { entered.complete(null); release.join() } }
      entered.get(5, TimeUnit.SECONDS)
      val stopped = lifecycle.closeAsync()
      assertThat(stopped).isNotDone()
      assertThatIllegalStateException().isThrownBy { lifecycle.deliver { fail("Closed lifecycle") } }
      release.complete(null)
      running.get(5, TimeUnit.SECONDS)
      stopped.get(5, TimeUnit.SECONDS)
      assertThat(contexts).hasValue(1)
    } finally {
      release.complete(null)
      executor.shutdownNow()
    }
  }

  @Test
  fun shutdownFromNestedDeliveryDoesNotWaitForItselfAndRestoresTheTokenDepth() {
    val lifecycle = DeliveryLifecycle(Runnable::run, Runnable::run)
    lifecycle.deliver {
      assertThat(lifecycle.inDelivery()).isTrue()
      lifecycle.deliver { assertThat(lifecycle.closeAsync()).isNotDone() }
      assertThat(lifecycle.inDelivery()).isTrue()
      assertThat(lifecycle.closeAsync()).isNotDone()
    }
    assertThat(lifecycle.inDelivery()).isFalse()
    assertThat(lifecycle.closeAsync()).isCompleted()
  }

  @Test
  fun shutdownCancelsUncommittedCallbacksWithoutWaitingForCallerOwnedCommit() {
    val lifecycle = DeliveryLifecycle(Runnable::run, Runnable::run)
    val ticket = lifecycle.defer()
    val calls = AtomicInteger()
    assertThat(lifecycle.closeAsync()).isCompleted()
    assertThat(lifecycle.resourcesReleased()).isNotDone()
    ticket.dispatch(calls::incrementAndGet, true)
    assertThat(calls).hasValue(0)
    assertThat(lifecycle.resourcesReleased()).isCompleted()
  }

  @Test
  fun queuedCommittedCallbackRetainsItsLeaseUntilItFinishes() {
    val queued = mutableListOf<Runnable>()
    val lifecycle = DeliveryLifecycle(queued::add, Runnable::run)
    val calls = AtomicInteger()
    lifecycle.defer().dispatch(calls::incrementAndGet, true)
    val stopped = lifecycle.closeAsync()
    assertThat(stopped).isNotDone()
    queued[0].run()
    assertThat(calls).hasValue(1)
    assertThat(stopped).isCompleted()
  }

  @Test
  fun rejectedManagedExecutorReportsFailureOnFallbackWithALiveLease() {
    val reported = CompletableFuture<Throwable>()
    val failure = RejectedExecutionException("executor stopped")
    val lifecycle = DeliveryLifecycle({ throw failure }, Runnable::run)
    lifecycle.defer().dispatch({ fail("Rejected completion must not execute") }, true, reported::complete)
    assertThat(reported.get(5, TimeUnit.SECONDS)).isSameAs(failure)
    lifecycle.closeAsync().get(5, TimeUnit.SECONDS)
  }

  @Test
  fun eventLoopDeliveryIsRejectedBeforeContextOrWorkerExecution() {
    val lifecycle = DeliveryLifecycle(Runnable::run) { fail("No context on an IO thread") }
    BlockingOperationControl.setIoThreadDetector(arrayOf(IOThreadDetector { true }))
    try {
      assertThatIllegalStateException().isThrownBy { lifecycle.deliver { fail("No worker on an IO thread") } }
        .withMessageContaining("event-loop")
    } finally {
      BlockingOperationControl.setIoThreadDetector(emptyArray())
    }
    assertThat(lifecycle.closeAsync()).isCompleted()
  }

  @Test
  fun failingSynchronousExecutorActionDoesNotDoubleReleaseTheLease() {
    val lifecycle = DeliveryLifecycle(Runnable::run, Runnable::run)
    assertThatIllegalArgumentException().isThrownBy {
      lifecycle.defer().dispatch({ throw IllegalArgumentException("handler failed") }, true)
    }
    assertThat(lifecycle.inDelivery()).isFalse()
    assertThat(lifecycle.closeAsync()).isCompleted()
  }
}
