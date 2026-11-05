package dev.bpmcrafters.processengine.worker.quarkus

import jakarta.transaction.Status
import jakarta.transaction.Synchronization
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class JtaWorkerTransactionExecutorTest {
  private val transactions = com.arjuna.ats.jta.TransactionManager.transactionManager()
  private val executor = JtaWorkerTransactionExecutor(transactions)

  @AfterEach
  fun cleanup() {
    if (transactions.transaction != null) transactions.rollback()
  }

  @Test
  fun returnsOnlyAfterCommit() {
    val events = mutableListOf<String>()
    val result = executor.execute {
      enlist(events)
      events.add("work")
      mapOf("result" to true)
    }
    assertThat(result).containsEntry("result", true)
    assertThat(events).containsExactly("work", "before", "committed")
  }

  @Test
  fun rollsBackAndPreservesOriginalFailure() {
    val events = mutableListOf<String>()
    val failure = IllegalArgumentException("business failure")
    assertThatThrownBy { executor.execute { enlist(events); throw failure } }.isSameAs(failure)
    assertThat(events).containsExactly("rolled back")
  }

  @Test
  fun propagatesRollbackOnlyCommitFailure() {
    assertThatThrownBy {
      executor.execute {
        try {
          transactions.setRollbackOnly()
        } catch (failure: Exception) {
          throw IllegalStateException(failure)
        }
        emptyMap()
      }
    }.isInstanceOf(jakarta.transaction.RollbackException::class.java)
  }

  @Test
  fun refusesAmbientTransactionWithoutRollingItBack() {
    transactions.begin()
    val ambient = transactions.transaction
    assertThatIllegalStateException().isThrownBy { executor.execute { emptyMap() } }.withMessageContaining("ambient")
    assertThat(transactions.transaction).isSameAs(ambient)
    assertThat(ambient.status).isEqualTo(Status.STATUS_ACTIVE)
  }

  private fun enlist(events: MutableList<String>) {
    try {
      transactions.transaction.registerSynchronization(object : Synchronization {
        override fun beforeCompletion() { events.add("before") }
        override fun afterCompletion(status: Int) {
          events.add(if (status == Status.STATUS_COMMITTED) "committed" else "rolled back")
        }
      })
    } catch (failure: Exception) {
      throw IllegalStateException(failure)
    }
  }
}
