package dev.bpmcrafters.processengine.worker.transaction

import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * After-commit hook based on Spring's [TransactionSynchronizationManager].
 * @since 0.8.6
 */
object SpringAfterCommitHook : AfterCommitHook {
  override fun afterCommitOrNow(block: () -> Unit) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
        override fun afterCommit() {
          block()
        }
      })
    } else {
      block()
    }
  }
}

/**
 * Transactional executor based on Spring's [TransactionTemplate].
 *
 * The template is not touched before the first transactional execution, so a lazily resolved template
 * (e.g. injected with `@Lazy`) is supported and applications without transaction manager start fine, as long
 * as no transactional worker is executed.
 * @since 0.8.6
 */
class SpringTransactionalExecutor(
  private val transactionTemplate: TransactionTemplate
) : TransactionalExecutor {

  /**
   * Executes the block using the template. Checked exceptions thrown by the block are wrapped by the template
   * into an `UndeclaredThrowableException` after rollback, which is unwrapped by the registrar.
   */
  override fun <T> executeInTransaction(block: () -> T): T {
    @Suppress("UNCHECKED_CAST")
    return transactionTemplate.execute { block() } as T
  }

  override fun afterCommitOrNow(block: () -> Unit) = SpringAfterCommitHook.afterCommitOrNow(block)
}
