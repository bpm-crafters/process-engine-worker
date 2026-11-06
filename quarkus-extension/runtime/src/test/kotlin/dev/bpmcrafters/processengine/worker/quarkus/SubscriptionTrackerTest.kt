package dev.bpmcrafters.processengine.worker.quarkus

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.task.*
import io.quarkus.runtime.BlockingOperationControl
import io.quarkus.runtime.IOThreadDetector
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SubscriptionTrackerTest {
  private val client = FakeSubscriptions()
  private val tracker = SubscriptionTracker(client)

  @BeforeEach
  fun initializePlainJvmThreadDetection() {
    BlockingOperationControl.setIoThreadDetector(emptyArray())
  }

  @Test
  fun acceptedDeliveryCanTerminateAfterRemoteCompletionWhileShutdownDrains() {
    val lifecycle = DeliveryLifecycle(Runnable::run, Runnable::run)
    val owned = SubscriptionTracker(client, lifecycle)
    val executor = Executors.newSingleThreadExecutor()
    val entered = CompletableFuture<Void>()
    val release = CompletableFuture<Void>()
    val remoteCompleted = AtomicInteger()
    val terminated = AtomicInteger()
    val task = TaskInformation("task", emptyMap())
    owned.subscribeForTask(SubscribeForTaskCmd(emptyMap(), TaskType.EXTERNAL, "topic", null,
      { _, _ ->
        entered.complete(null)
        release.get(5, TimeUnit.SECONDS)
        remoteCompleted.incrementAndGet()
        client.managed.termination.accept(task)
      }, TaskTerminationHandler { terminated.incrementAndGet() })).join()
    try {
      val running = executor.submit { client.managed.action.accept(task, emptyMap()) }
      entered.get(5, TimeUnit.SECONDS)
      val stopped = owned.closeAsync()
      assertThat(stopped).isNotDone()
      assertThatIllegalStateException().isThrownBy { client.managed.action.accept(task, emptyMap()) }
        .withMessage("Worker runtime is closed")
      release.complete(null)
      running.get(5, TimeUnit.SECONDS)
      stopped.get(5, TimeUnit.SECONDS)
      assertThat(remoteCompleted).hasValue(1)
      assertThat(terminated).hasValue(1)
      assertThat(lifecycle.resourcesReleased()).isCompleted()
    } finally {
      release.complete(null)
      executor.shutdownNow()
    }
  }

  @Test
  fun terminationOnAnotherThreadRetainsResourcesUntilItsCallbackReturns() {
    val contexts = AtomicInteger()
    val lifecycle = DeliveryLifecycle(Runnable::run) { action ->
      contexts.incrementAndGet()
      try { action.run() } finally { contexts.decrementAndGet() }
    }
    val owned = SubscriptionTracker(client, lifecycle)
    val executor = Executors.newFixedThreadPool(2)
    val entered = CompletableFuture<Void>()
    val releaseWorker = CompletableFuture<Void>()
    val callbackEntered = CompletableFuture<Void>()
    val releaseCallback = CompletableFuture<Void>()
    val task = TaskInformation("task", emptyMap())
    owned.subscribeForTask(SubscribeForTaskCmd(emptyMap(), TaskType.EXTERNAL, "topic", null,
      { _, _ -> entered.complete(null); releaseWorker.get(5, TimeUnit.SECONDS) },
      TaskTerminationHandler {
        assertThat(contexts.get()).isPositive()
        callbackEntered.complete(null)
        releaseCallback.get(5, TimeUnit.SECONDS)
      })).join()
    try {
      val running = executor.submit { client.managed.action.accept(task, emptyMap()) }
      entered.get(5, TimeUnit.SECONDS)
      val stopped = owned.closeAsync()
      val callback = CompletableFuture.runAsync({ client.managed.termination.accept(task) }, executor)
      callback.whenComplete { _, failure -> if (failure != null) callbackEntered.completeExceptionally(failure) }
      // Propagate an early callback failure rather than masking it as a latch timeout.
      callbackEntered.get(5, TimeUnit.SECONDS)
      assertThat(callbackEntered).isCompleted()
      assertThat(contexts).hasValue(2)
      releaseWorker.complete(null)
      running.get(5, TimeUnit.SECONDS)
      assertThat(stopped).isNotDone()
      assertThat(lifecycle.resourcesReleased()).isNotDone()
      releaseCallback.complete(null)
      callback.get(5, TimeUnit.SECONDS)
      stopped.get(5, TimeUnit.SECONDS)
      assertThat(lifecycle.resourcesReleased()).isCompleted()
      assertThat(contexts).hasValue(0)
    } finally {
      releaseWorker.complete(null)
      releaseCallback.complete(null)
      executor.shutdownNow()
    }
  }

  @Test
  fun unsubscribeAndLateTerminationDoNotReenterReleasedContexts() {
    val contexts = AtomicInteger()
    val terminated = AtomicInteger()
    val lifecycle = DeliveryLifecycle(Runnable::run) { action -> contexts.incrementAndGet(); action.run() }
    val owned = SubscriptionTracker(client, lifecycle)
    val task = TaskInformation("task", emptyMap())
    owned.subscribeForTask(SubscribeForTaskCmd(emptyMap(), TaskType.EXTERNAL, "topic", null,
      { _, _ -> }, TaskTerminationHandler { terminated.incrementAndGet() })).join()
    client.onUnsubscribe = { client.managed.termination.accept(task) }
    owned.closeAsync().get(5, TimeUnit.SECONDS)
    assertThat(lifecycle.resourcesReleased()).isCompleted()
    client.managed.termination.accept(task)
    assertThat(contexts).hasValue(0)
    assertThat(terminated).hasValue(0)
  }

  @Test
  fun lateTerminationAfterSuccessfulShutdownDoesNotAccessDisposedContexts() {
    val contexts = AtomicInteger()
    val terminated = AtomicInteger()
    val lifecycle = DeliveryLifecycle(Runnable::run) { action -> contexts.incrementAndGet(); action.run() }
    val owned = SubscriptionTracker(client, lifecycle)
    owned.subscribeForTask(SubscribeForTaskCmd(emptyMap(), TaskType.EXTERNAL, "topic", null,
      { _, _ -> }, TaskTerminationHandler { terminated.incrementAndGet() })).join()
    owned.closeAsync().get(5, TimeUnit.SECONDS)
    assertThat(lifecycle.resourcesReleased()).isCompleted()
    BlockingOperationControl.setIoThreadDetector(arrayOf(IOThreadDetector { true }))
    try {
      // Ignoring a late adapter notification must not require a blocking/request context.
      client.managed.termination.accept(TaskInformation("task", emptyMap()))
    } finally {
      BlockingOperationControl.setIoThreadDetector(emptyArray())
    }
    assertThat(contexts).hasValue(0)
    assertThat(terminated).hasValue(0)
  }

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
    lateinit var managed: SubscribeForTaskCmd
    var onUnsubscribe: () -> Unit = {}

    override fun subscribeForTask(command: SubscribeForTaskCmd): CompletableFuture<TaskSubscription> {
      managed = command
      if (throwOnSubscribe) throw IllegalArgumentException("synchronous subscribe failure")
      return next ?: CompletableFuture.completedFuture(object : TaskSubscription {})
    }

    override fun unsubscribe(command: UnsubscribeFromTaskCmd): CompletableFuture<Empty> {
      removed.add(command.subscription)
      onUnsubscribe()
      if (throwOnUnsubscribe) throw IllegalArgumentException("synchronous unsubscribe failure")
      return unsubscribeResult ?: if (fail) CompletableFuture.failedFuture(IllegalStateException("unsubscribe"))
      else CompletableFuture.completedFuture(Empty)
    }

    override fun getSupportedRestrictions(): Set<String> = setOf("tenant")
    override fun meta(instance: MetaInfoAware): MetaInfo = metadata
    override fun close() { closed = true }
  }
}
