package dev.bpmcrafters.processengine.worker.registrar

/**
 * One delivery, including cache replay. Framework adapters own scheduling/transactions, not worker semantics.
 * Call [processResult] at the managed invocation's transaction boundary and [complete] at most once.
 * Deferred failures must use [reportFailure]; synchronous failures can propagate to the registrar.
 */
interface WorkerDelivery {
  fun autoComplete(): Boolean
  fun completeBeforeCommit(): Boolean
  fun cachedResult(): Map<String, Any?>?

  @Throws(Exception::class)
  fun invoke(): Any?

  @Throws(Exception::class)
  fun processResult(result: Any?): Map<String, Any?>

  @Throws(Exception::class)
  fun complete(result: Map<String, Any?>)

  /** Consumes a terminal failure once. Reporting errors must retain the original failure, not escape as a new delivery failure. */
  fun reportFailure(failure: Throwable)
}

/** Additive delivery coordination; legacy registrar overloads retain their original transaction executor. */
fun interface WorkerDeliveryCoordinator {
  @Throws(Exception::class)
  fun deliver(delivery: WorkerDelivery)
}
