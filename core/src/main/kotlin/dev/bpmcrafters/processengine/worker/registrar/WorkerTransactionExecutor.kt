package dev.bpmcrafters.processengine.worker.registrar

import java.util.function.Supplier

/**
 * Executes the action atomically and returns only after commit. Rollback or commit failures must propagate.
 * Adapters must also arrange transaction-aware idempotency storage when required.
 * @since 0.9.0
 **/
fun interface WorkerTransactionExecutor {
  /**
   * Runs the action within the framework transaction and returns its payload after commit.
   **/
  fun execute(action: Supplier<Map<String, Any?>>): Map<String, Any?>
}
