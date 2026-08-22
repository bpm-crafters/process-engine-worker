package dev.bpmcrafters.processengine.worker.quarkus

import dev.bpmcrafters.processengine.worker.transaction.TransactionalExecutor
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.arc.DefaultBean
import io.quarkus.narayana.jta.QuarkusTransactionException
import jakarta.transaction.Status
import jakarta.transaction.Synchronization
import jakarta.transaction.TransactionSynchronizationRegistry
import java.util.concurrent.Callable

/**
 * Transactional executor based on JTA (`quarkus-narayana-jta`), using [QuarkusTransaction] with `REQUIRED` semantics.
 *
 * This class carries no bean-defining annotation on purpose: it is registered as a `@Singleton` bean by the deployment
 * module only if the transactions capability is present (it must not be discovered from the Jandex index otherwise).
 * It is a `@DefaultBean`, so an application-provided [TransactionalExecutor] bean wins.
 *
 * @param synchronizationRegistry JTA synchronization registry used to defer actions until after commit.
 * @since 0.8.6
 */
@DefaultBean
open class JtaTransactionalExecutor(
  private val synchronizationRegistry: TransactionSynchronizationRegistry
) : TransactionalExecutor {

  /**
   * Runs the block joining an existing transaction or starting a new one. [QuarkusTransaction] wraps any throwable which is
   * not a `RuntimeException` (checked exceptions, but also errors and commit failures) into a [QuarkusTransactionException],
   * which is unwrapped here so that the registrar sees the original cause.
   */
  override fun <T> executeInTransaction(block: () -> T): T {
    try {
      return runInTransaction { block() }
    } catch (e: QuarkusTransactionException) {
      throw e.cause ?: e
    }
  }

  /**
   * Seam for the actual transaction runner.
   * @param callable callable to run in transaction.
   * @return result.
   */
  protected open fun <T> runInTransaction(callable: Callable<T>): T = QuarkusTransaction.joiningExisting().call(callable)

  /**
   * Defers the block until the active transaction has been committed. Without a transaction, the block runs immediately.
   * If the transaction is already marked for rollback or completing, the block is skipped (it could never run after a commit),
   * mirroring Spring's `afterCommit` synchronization instead of failing on the synchronization registration.
   */
  override fun afterCommitOrNow(block: () -> Unit) {
    when (synchronizationRegistry.transactionStatus) {
      Status.STATUS_NO_TRANSACTION -> block()
      Status.STATUS_ACTIVE -> synchronizationRegistry.registerInterposedSynchronization(object : Synchronization {
        override fun beforeCompletion() {}
        override fun afterCompletion(status: Int) {
          if (status == Status.STATUS_COMMITTED) {
            block()
          }
        }
      })

      else -> Unit // marked rollback-only, preparing, committing or completed: the block would never run after a commit
    }
  }
}
