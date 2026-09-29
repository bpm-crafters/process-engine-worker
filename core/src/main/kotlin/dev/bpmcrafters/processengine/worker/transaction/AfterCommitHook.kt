package dev.bpmcrafters.processengine.worker.transaction

/**
 * Hook allowing to defer an action until the currently active transaction has been committed.
 * If no transaction is active, the action is executed immediately.
 *
 * Implementations are provided by the framework integration (e.g. Spring's `TransactionSynchronizationManager`
 * or Jakarta's `TransactionSynchronizationRegistry`).
 * @since 0.8.6
 */
fun interface AfterCommitHook {

  /**
   * Executes the block after commit of the active transaction or immediately, if no transaction is active.
   * @param block block to execute.
   */
  fun afterCommitOrNow(block: () -> Unit)

  companion object {
    /**
     * Hook executing the block immediately, suitable if no transaction management is available.
     */
    @JvmField
    val IMMEDIATE: AfterCommitHook = AfterCommitHook { block -> block() }
  }
}

/**
 * Marks a component (e.g. an idempotency registry) that needs an [AfterCommitHook] provided by the framework integration.
 * The hook is installed by the framework layer (Spring Boot starter, Quarkus extension) on the raw instance.
 * @since 0.8.6
 */
interface AfterCommitHookAware {
  /**
   * Hook to defer actions until the active transaction has been committed.
   */
  var afterCommitHook: AfterCommitHook
}
