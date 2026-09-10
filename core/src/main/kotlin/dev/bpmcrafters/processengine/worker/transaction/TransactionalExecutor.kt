package dev.bpmcrafters.processengine.worker.transaction

/**
 * Abstraction of the transaction management used by the worker registrar to execute transactional workers.
 *
 * Implementations are provided by the framework integration (Spring `TransactionTemplate`, Quarkus `QuarkusTransaction`).
 * @since 0.8.6
 */
interface TransactionalExecutor : AfterCommitHook {

  /**
   * Executes the block inside a transaction using `REQUIRED` semantics: joins an active transaction or
   * creates a new one. If the block throws, the transaction is rolled back (or marked rollback-only) and the
   * exception is re-thrown. Implementations must either re-throw the original throwable or wrap it in one of the
   * types unwrapped by [dev.bpmcrafters.processengine.worker.registrar.ExceptionResolver].
   *
   * @param block block to execute.
   * @return result of the block.
   */
  fun <T> executeInTransaction(block: () -> T): T

  companion object {
    /**
     * Executor running blocks directly without any transaction management.
     */
    @JvmField
    val NONE: TransactionalExecutor = object : TransactionalExecutor {
      override fun <T> executeInTransaction(block: () -> T): T = block()
      override fun afterCommitOrNow(block: () -> Unit) = block()
      override fun toString(): String = "TransactionalExecutor.NONE"
    }
  }
}
