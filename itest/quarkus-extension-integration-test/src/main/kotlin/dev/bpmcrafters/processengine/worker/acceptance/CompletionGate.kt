package dev.bpmcrafters.processengine.worker.acceptance

import dev.bpmcrafters.processengineapi.Empty
import java.util.concurrent.CompletableFuture

/** A durable, first-release-wins decision, including when release precedes registration. */
class CompletionGate {
  private val decision = CompletableFuture<Empty>()
  @Volatile private var registered = false

  fun register(): CompletableFuture<Empty> {
    registered = true
    return decision
  }

  fun release(success: Boolean) {
    if (success) decision.complete(Empty)
    else decision.completeExceptionally(IllegalStateException("Released fixture failure"))
  }

  fun isPending(): Boolean = registered && !decision.isDone
}
