package dev.bpmcrafters.processengine.worker.quarkus

import io.quarkus.narayana.jta.QuarkusTransactionException
import jakarta.transaction.Status
import jakarta.transaction.Synchronization
import jakarta.transaction.TransactionSynchronizationRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException
import java.util.concurrent.Callable

/**
 * Unit test of the exception unwrapping and the after-commit hook, using a seam instead of a real transaction manager.
 */
class JtaTransactionalExecutorTest {

  private val registry: TransactionSynchronizationRegistry = mock()

  /**
   * Mimics `QuarkusTransaction.joiningExisting().call()`: wraps everything except runtime exceptions.
   */
  private fun executor(failure: Throwable? = null) = object : JtaTransactionalExecutor(registry) {
    override fun <T> runInTransaction(callable: Callable<T>): T {
      if (failure != null) {
        throw failure
      }
      return try {
        callable.call()
      } catch (e: RuntimeException) {
        throw e
      } catch (e: Throwable) {
        throw QuarkusTransactionException(e)
      }
    }
  }

  @Test
  fun `returns result of block`() {
    assertThat(executor().executeInTransaction { "result" }).isEqualTo("result")
  }

  @Test
  fun `unwraps checked exception`() {
    val checked = IOException("checked")
    assertThatThrownBy { executor().executeInTransaction { throw checked } }.isSameAs(checked)
  }

  @Test
  fun `unwraps error`() {
    val error = AssertionError("error")
    assertThatThrownBy { executor().executeInTransaction { throw error } }.isSameAs(error)
  }

  @Test
  fun `rethrows runtime exception as is`() {
    val runtime = IllegalStateException("runtime")
    assertThatThrownBy { executor().executeInTransaction { throw runtime } }.isSameAs(runtime)
  }

  @Test
  fun `unwraps commit failure`() {
    val commitFailure = jakarta.transaction.RollbackException("commit failed")
    assertThatThrownBy { executor(QuarkusTransactionException("Transaction rolled back", commitFailure)).executeInTransaction { "never" } }
      .isSameAs(commitFailure)
  }

  @Test
  fun `rethrows transaction exception without cause`() {
    val failure = QuarkusTransactionException("no cause")
    assertThatThrownBy { executor(failure).executeInTransaction { "never" } }.isSameAs(failure)
  }

  @Test
  fun `runs block immediately without transaction`() {
    whenever(registry.transactionStatus).thenReturn(Status.STATUS_NO_TRANSACTION)
    var executed = false
    executor().afterCommitOrNow { executed = true }
    assertThat(executed).isTrue()
  }

  @Test
  fun `defers block until commit inside transaction`() {
    whenever(registry.transactionStatus).thenReturn(Status.STATUS_ACTIVE)
    var executed = false
    executor().afterCommitOrNow { executed = true }
    assertThat(executed).isFalse()

    val synchronization = argumentCaptor<Synchronization>()
    verify(registry).registerInterposedSynchronization(synchronization.capture())

    synchronization.firstValue.afterCompletion(Status.STATUS_ROLLEDBACK)
    assertThat(executed).isFalse()
    synchronization.firstValue.afterCompletion(Status.STATUS_COMMITTED)
    assertThat(executed).isTrue()
  }

  @Test
  fun `skips block if transaction is marked for rollback`() {
    whenever(registry.transactionStatus).thenReturn(Status.STATUS_MARKED_ROLLBACK)
    var executed = false
    executor().afterCommitOrNow { executed = true }
    assertThat(executed).isFalse()
    verify(registry, never()).registerInterposedSynchronization(any())
  }

  @Test
  fun `skips block if transaction is completing`() {
    whenever(registry.transactionStatus).thenReturn(Status.STATUS_COMMITTING)
    var executed = false
    executor().afterCommitOrNow { executed = true }
    assertThat(executed).isFalse()
    verify(registry, never()).registerInterposedSynchronization(any())
  }
}
