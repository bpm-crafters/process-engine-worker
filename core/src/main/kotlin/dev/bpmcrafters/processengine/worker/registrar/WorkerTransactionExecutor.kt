package dev.bpmcrafters.processengine.worker.registrar

import java.util.function.Supplier

/**
 * Executes the action atomically and returns only after commit. Rollback or commit failures must propagate.
 * Adapters must also arrange transaction-aware idempotency storage when required.
 */
fun interface WorkerTransactionExecutor {
  fun execute(action: Supplier<Map<String, Any?>>): Map<String, Any?>
}
