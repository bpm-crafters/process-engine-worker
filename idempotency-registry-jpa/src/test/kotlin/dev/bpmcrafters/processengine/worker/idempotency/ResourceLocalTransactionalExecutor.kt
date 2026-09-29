package dev.bpmcrafters.processengine.worker.idempotency

import dev.bpmcrafters.processengine.worker.transaction.TransactionalExecutor
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory

/**
 * Test-only resource-local transactional executor. Mirrors the transaction-scoped persistence context of
 * Spring's shared entity manager / Quarkus' session: a fresh [EntityManager] is opened for the outermost
 * transaction and closed after commit or rollback. Nested calls join the active transaction (`REQUIRED`
 * semantics), after-commit actions run once the outermost transaction has been committed.
 */
class ResourceLocalTransactionalExecutor(
  private val entityManagerFactory: EntityManagerFactory
) : TransactionalExecutor {

  private var current: EntityManager? = null
  private val afterCommitActions = mutableListOf<() -> Unit>()

  /**
   * Entity manager bound to the active transaction.
   */
  fun currentEntityManager(): EntityManager =
    checkNotNull(current) { "No transaction active" }

  fun isTransactionActive(): Boolean = current != null

  override fun <T> executeInTransaction(block: () -> T): T {
    current?.let { em ->
      return try {
        block()
      } catch (e: Throwable) {
        em.transaction.setRollbackOnly()
        throw e
      }
    }
    val em = entityManagerFactory.createEntityManager()
    current = em
    val tx = em.transaction
    tx.begin()
    try {
      val result = block()
      if (tx.rollbackOnly) {
        tx.rollback()
        afterCommitActions.clear()
      } else {
        tx.commit()
        val actions = afterCommitActions.toList()
        afterCommitActions.clear()
        actions.forEach { it() }
      }
      return result
    } catch (e: Throwable) {
      if (tx.isActive) {
        tx.rollback()
      }
      afterCommitActions.clear()
      throw e
    } finally {
      current = null
      em.close()
    }
  }

  override fun afterCommitOrNow(block: () -> Unit) {
    if (current != null) {
      afterCommitActions.add(block)
    } else {
      block()
    }
  }
}
