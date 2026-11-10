package dev.bpmcrafters.processengine.worker.quarkus

import dev.bpmcrafters.processengine.worker.registrar.WorkerTransactionExecutor
import jakarta.transaction.Status
import jakarta.transaction.TransactionManager
import java.util.function.Supplier

/** Owns the transaction surrounding REQUIRED workers; CDI REQUIRED joins it. */
class JtaWorkerTransactionExecutor(private val transactions: TransactionManager) : WorkerTransactionExecutor {
  override fun execute(action: Supplier<Map<String, Any?>>): Map<String, Any?> {
    check(transactions.status == Status.STATUS_NO_TRANSACTION) {
      "Worker execution requires an owned transaction; ambient transactions cannot guarantee AFTER_COMMIT completion"
    }
    transactions.begin()
    try {
      val result = action.get()
      transactions.commit()
      return result
    } catch (failure: Throwable) {
      rollback(failure)
      throw failure
    }
  }

  private fun rollback(failure: Throwable) {
    try {
      if (transactions.status != Status.STATUS_NO_TRANSACTION) transactions.rollback()
    } catch (rollbackFailure: Exception) {
      failure.addSuppressed(rollbackFailure)
    }
  }
}
