package dev.bpmcrafters.processengine.worker.quarkus

import io.quarkus.arc.Arc
import io.quarkus.runtime.BlockingOperationControl
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/** Drains running work, but cancels uncommitted callbacks rather than waiting for caller-owned commits. */
class DeliveryLifecycle(
  private val executor: Executor,
  private val contextual: Consumer<Runnable>
) {
  constructor(executor: Executor) : this(executor, Consumer(::requestScoped))

  private val pending = HashSet<Ticket>()
  private val drained = CompletableFuture<Void>()
  private val resourcesReleased = CompletableFuture<Void>()
  private val depth = ThreadLocal<Int>()
  private var active = 0
  private var closed = false

  fun deliver(action: Runnable) {
    check(BlockingOperationControl.isBlockingAllowed()) {
      "Process engine task delivery requires a blocking-capable thread, not an event-loop thread"
    }
    synchronized(this) {
      check(!closed) { "Worker runtime is closed" }
      active++
    }
    run(action)
  }

  /** Adapter notifications may finish accepted work, but must never revive a drained runtime. */
  fun terminate(action: Runnable) {
    synchronized(this) {
      // Once closed and idle, no admission path can acquire a new lease.
      if (closed && active == 0) return
      check(BlockingOperationControl.isBlockingAllowed()) {
        "Process engine task delivery requires a blocking-capable thread, not an event-loop thread"
      }
      active++
    }
    run(action)
  }

  private fun run(action: Runnable) {
    depth.set(currentDepth() + 1)
    try {
      contextual.accept(action)
    } finally {
      val remaining = currentDepth() - 1
      if (remaining == 0) depth.remove() else depth.set(remaining)
      synchronized(this) { active-- }
      finishDrain()
    }
  }

  fun defer(): Ticket = synchronized(this) {
    Ticket().also {
      it.cancelled = closed
      pending.add(it)
    }
  }

  fun closeAsync(): CompletableFuture<Void> {
    synchronized(this) {
      closed = true
      pending.forEach { it.cancelled = true }
    }
    finishDrain()
    return drained
  }

  fun inDelivery(): Boolean = currentDepth() != 0

  /** Retains dependent references until synchronizations finish, without delaying shutdown. */
  fun resourcesReleased(): CompletableFuture<Void> = resourcesReleased

  private fun currentDepth(): Int = depth.get() ?: 0

  private fun finishDrain() {
    val release = synchronized(this) {
      if (!closed || active != 0) return
      pending.isEmpty()
    }
    drained.complete(null)
    if (release) resourcesReleased.complete(null)
  }

  inner class Ticket {
    var cancelled = false

    fun cancel() {
      synchronized(this@DeliveryLifecycle) { cancelled = true }
    }

    fun discard() {
      synchronized(this@DeliveryLifecycle) { pending.remove(this) }
      finishDrain()
    }

    fun dispatch(action: Runnable, asynchronous: Boolean) {
      dispatch(action, asynchronous) { throw CompletionException(it) }
    }

    fun dispatch(action: Runnable, asynchronous: Boolean, rejectedAction: Consumer<Throwable>) {
      val execute = synchronized(this@DeliveryLifecycle) {
        (pending.remove(this) && !closed && !cancelled).also { if (it) active++ }
      }
      if (!execute) {
        finishDrain()
        return
      }
      if (!asynchronous && BlockingOperationControl.isBlockingAllowed()) {
        run(action)
        return
      }
      schedule(action, rejectedAction)
    }

    private fun schedule(action: Runnable, rejectedAction: Consumer<Throwable>) {
      val started = AtomicBoolean()
      try {
        executor.execute { started.set(true); run(action) }
      } catch (rejected: RuntimeException) {
        if (started.get()) throw rejected
        // Retain the lease/context while reporting rejection, even during managed-executor shutdown.
        ForkJoinPool.commonPool().execute { run(Runnable { rejectedAction.accept(rejected) }) }
      }
    }
  }

  companion object {
    private fun requestScoped(action: Runnable) {
      val context = Arc.container().requestContext()
      val activate = !context.isActive
      if (activate) context.activate()
      try {
        action.run()
      } finally {
        if (activate) context.terminate()
      }
    }
  }
}
