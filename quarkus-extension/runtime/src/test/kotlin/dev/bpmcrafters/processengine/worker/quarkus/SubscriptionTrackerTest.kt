package dev.bpmcrafters.processengine.worker.quarkus

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.task.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture

class SubscriptionTrackerTest {
  private val client = FakeSubscriptions()
  private val tracker = SubscriptionTracker(client)

  @Test
  fun ownsAllHandlesAndClosesOnlyOnce() {
    val first = tracker.subscribeForTask(command()).join()
    val second = tracker.subscribeForTask(command()).join()
    tracker.close()
    tracker.close()
    assertThat(client.removed).containsExactly(first, second)
    assertThat(client.closed).isFalse()
    assertThat(tracker.getSupportedRestrictions()).containsExactly("tenant")
    assertThat(tracker.meta(client)).isSameAs(client.metadata)
  }

  @Test
  fun unsubscribesLateHandleAfterShutdown() {
    val next = CompletableFuture<TaskSubscription>()
    client.next = next
    val future = tracker.subscribeForTask(command())
    val stopping = tracker.closeAsync()
    val handle = object : TaskSubscription {}
    next.complete(handle)
    assertThatThrownBy { future.join() }.hasCauseInstanceOf(IllegalStateException::class.java)
    assertThat(client.removed).containsExactly(handle)
    assertThat(stopping).isCompleted()
  }

  @Test
  fun attemptsEveryUnsubscribeDespiteFailures() {
    tracker.subscribeForTask(command()).join()
    tracker.subscribeForTask(command()).join()
    client.fail = true
    assertThatThrownBy { tracker.close() }.hasCauseInstanceOf(IllegalStateException::class.java)
      .satisfies(java.util.function.Consumer { failure -> assertThat(failure.cause!!.suppressed).hasSize(1) })
    assertThat(client.removed).hasSize(2)
  }

  @Test
  fun startsEveryAsynchronousUnsubscribeAndDrainsBeforeReleasingResources() {
    tracker.subscribeForTask(command()).join()
    tracker.subscribeForTask(command()).join()
    val unsubscribeResult = CompletableFuture<Empty>()
    client.unsubscribeResult = unsubscribeResult
    val stopped = tracker.closeAsync()
    assertThat(client.removed).hasSize(2)
    assertThat(stopped).isNotDone()
    unsubscribeResult.complete(Empty)
    assertThat(stopped).isCompleted()
    assertThat(client.closed).isFalse()
  }

  @Test
  fun lateAcquisitionKeepsShutdownPendingUntilItsAsynchronousUnsubscribeCompletes() {
    val next = CompletableFuture<TaskSubscription>()
    val unsubscribeResult = CompletableFuture<Empty>()
    client.next = next
    client.unsubscribeResult = unsubscribeResult
    val acquired = tracker.subscribeForTask(command())
    val stopped = tracker.closeAsync()
    assertThat(stopped).isNotDone()
    next.complete(object : TaskSubscription {})
    assertThat(stopped).isNotDone()
    assertThat(acquired).isNotDone()
    unsubscribeResult.complete(Empty)
    assertThat(stopped).isCompleted()
    assertThatThrownBy { acquired.join() }.hasCauseInstanceOf(IllegalStateException::class.java)
  }

  @Test
  fun failedAcquisitionAndClosedSubscribeDoNotLeakOwnership() {
    val next = CompletableFuture<TaskSubscription>()
    client.next = next
    val acquired = tracker.subscribeForTask(command())
    val stopped = tracker.closeAsync()
    next.completeExceptionally(IllegalStateException("subscribe failed"))
    assertThat(stopped).isCompleted()
    assertThatThrownBy { acquired.join() }.hasRootCauseMessage("subscribe failed")
    assertThatThrownBy { tracker.subscribeForTask(command()).join() }.hasRootCauseMessage("Worker runtime is closed")
    assertThat(client.removed).isEmpty()
  }

  @Test
  fun synchronousUnsubscribeFailuresStillAttemptEveryHandle() {
    tracker.subscribeForTask(command()).join()
    tracker.subscribeForTask(command()).join()
    client.throwOnUnsubscribe = true
    assertThatThrownBy { tracker.close() }.hasRootCauseMessage("synchronous unsubscribe failure")
    assertThat(client.removed).hasSize(2)
    assertThat(client.closed).isFalse()
  }

  @Test
  fun synchronousSubscribeFailureReleasesThePendingAcquisition() {
    client.throwOnSubscribe = true
    assertThatThrownBy { tracker.subscribeForTask(command()).join() }.hasRootCauseMessage("synchronous subscribe failure")
    assertThat(tracker.closeAsync()).isCompleted()
  }

  @Test
  fun invalidNullHandleFailsClearlyAndDoesNotRetainPendingOwnership() {
    client.next = CompletableFuture.completedFuture(null)
    assertThatThrownBy { tracker.subscribeForTask(command()).join() }
      .hasRootCauseMessage("Engine returned a null task subscription handle")
    assertThat(tracker.closeAsync()).isCompleted()
  }

  private fun command() = SubscribeForTaskCmd(
    emptyMap(), TaskType.EXTERNAL, "topic", null, { _, _ -> }, TaskTerminationHandler { }
  )

  private class FakeSubscriptions : TaskSubscriptionApi, AutoCloseable {
    val removed = mutableListOf<TaskSubscription>()
    val metadata = object : MetaInfo {}
    var next: CompletableFuture<TaskSubscription>? = null
    var unsubscribeResult: CompletableFuture<Empty>? = null
    var fail = false
    var closed = false
    var throwOnSubscribe = false
    var throwOnUnsubscribe = false

    override fun subscribeForTask(command: SubscribeForTaskCmd): CompletableFuture<TaskSubscription> {
      if (throwOnSubscribe) throw IllegalArgumentException("synchronous subscribe failure")
      return next ?: CompletableFuture.completedFuture(object : TaskSubscription {})
    }

    override fun unsubscribe(command: UnsubscribeFromTaskCmd): CompletableFuture<Empty> {
      removed.add(command.subscription)
      if (throwOnUnsubscribe) throw IllegalArgumentException("synchronous unsubscribe failure")
      return unsubscribeResult ?: if (fail) CompletableFuture.failedFuture(IllegalStateException("unsubscribe"))
      else CompletableFuture.completedFuture(Empty)
    }

    override fun getSupportedRestrictions(): Set<String> = setOf("tenant")
    override fun meta(instance: MetaInfoAware): MetaInfo = metadata
    override fun close() { closed = true }
  }
}
