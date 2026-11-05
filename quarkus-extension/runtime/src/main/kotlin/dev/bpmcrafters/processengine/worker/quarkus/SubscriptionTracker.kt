package dev.bpmcrafters.processengine.worker.quarkus

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.task.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

/** Owns subscription handles, not engine clients. Stop waits for late handles and deliveries to drain. */
class SubscriptionTracker(
  private val delegate: TaskSubscriptionApi,
  private val deliveries: DeliveryLifecycle
) : TaskSubscriptionApi, AutoCloseable {
  constructor(delegate: TaskSubscriptionApi) : this(delegate, DeliveryLifecycle(Runnable::run))

  private val subscriptions = mutableListOf<TaskSubscription>()
  private val stopped = CompletableFuture<Void>()
  private var subscribing = 0
  private var unsubscribing = 0
  private var closed = false
  private var drained = false
  private var failure: Throwable? = null

  override fun subscribeForTask(command: SubscribeForTaskCmd): CompletableFuture<TaskSubscription> {
    synchronized(this) {
      if (closed) return CompletableFuture.failedFuture(IllegalStateException("Worker runtime is closed"))
      subscribing++
    }
    val observed = CompletableFuture<TaskSubscription>()
    try {
      delegate.subscribeForTask(managed(command)).whenComplete { subscription, error -> acquired(subscription, error, observed) }
    } catch (error: RuntimeException) {
      acquired(null, error, observed)
    }
    return observed
  }

  private fun managed(command: SubscribeForTaskCmd) = SubscribeForTaskCmd(
    command.restrictions, command.taskType, command.taskDescriptionKey, command.payloadDescription,
    { task, payload -> deliveries.deliver { command.action.accept(task, payload) } },
    TaskTerminationHandler { task -> deliveries.deliver { command.termination.accept(task) } }
  )

  private fun acquired(subscription: TaskSubscription?, error: Throwable?, observed: CompletableFuture<TaskSubscription>) {
    val late = synchronized(this) {
      subscribing--
      (subscription != null && closed).also {
        if (it) unsubscribing++ else if (subscription != null) subscriptions.add(subscription)
      }
    }
    finishStop()
    when {
      error != null -> observed.completeExceptionally(error)
      subscription == null -> observed.completeExceptionally(IllegalStateException("Engine returned a null task subscription handle"))
      !late -> observed.complete(subscription)
      else -> unsubscribeOwned(subscription).whenComplete { _, unsubscribeError ->
        observed.completeExceptionally(unsubscribeError ?: IllegalStateException("Worker runtime closed during subscription"))
      }
    }
  }

  override fun unsubscribe(command: UnsubscribeFromTaskCmd): CompletableFuture<Empty> = delegate.unsubscribe(command)
  override fun getSupportedRestrictions(): Set<String> = delegate.getSupportedRestrictions()
  override fun meta(instance: MetaInfoAware): MetaInfo = delegate.meta(instance)

  fun closeAsync(): CompletableFuture<Void> {
    val handles = synchronized(this) {
      if (closed) return stopped
      closed = true
      subscriptions.toList().also {
        subscriptions.clear()
        unsubscribing += it.size
      }
    }
    deliveries.closeAsync().whenComplete { _, error ->
      synchronized(this) {
        drained = true
        recordFailure(error)
      }
      finishStop()
    }
    handles.forEach(::unsubscribeOwned)
    return stopped
  }

  private fun unsubscribeOwned(handle: TaskSubscription): CompletableFuture<Empty> {
    val future = try {
      java.util.Objects.requireNonNull(delegate.unsubscribe(UnsubscribeFromTaskCmd(handle)), "Engine returned a null unsubscribe future")
    } catch (error: RuntimeException) {
      finishedUnsubscribe(error)
      return CompletableFuture.failedFuture(error)
    }
    return future.whenComplete { _, error -> finishedUnsubscribe(error) }
  }

  private fun finishedUnsubscribe(error: Throwable?) {
    synchronized(this) {
      unsubscribing--
      recordFailure(error)
    }
    finishStop()
  }

  private fun recordFailure(error: Throwable?) {
    if (error == null) return
    val cause = if (error is CompletionException) error.cause ?: error else error
    val previous = failure
    if (previous == null) failure = cause else if (cause !== previous) previous.addSuppressed(cause)
  }

  private fun finishStop() {
    val problem = synchronized(this) {
      if (!closed || !drained || subscribing != 0 || unsubscribing != 0) return
      failure
    }
    if (problem == null) stopped.complete(null) else stopped.completeExceptionally(problem)
  }

  override fun close() {
    val stopping = closeAsync()
    // A handler-initiated shutdown must not wait for that same handler to return.
    if (!deliveries.inDelivery()) stopping.join()
  }
}
