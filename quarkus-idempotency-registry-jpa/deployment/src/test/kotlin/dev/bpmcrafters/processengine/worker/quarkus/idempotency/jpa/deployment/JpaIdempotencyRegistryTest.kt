package dev.bpmcrafters.processengine.worker.quarkus.idempotency.jpa.deployment

import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.quarkus.idempotency.jpa.JpaIdempotencyRegistry
import dev.bpmcrafters.processengine.worker.quarkus.idempotency.jpa.TaskLogEntry
import dev.bpmcrafters.processengine.worker.quarkus.idempotency.jpa.deployment.RegistryTestData.task
import dev.bpmcrafters.processengineapi.task.TaskInformation
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.narayana.jta.QuarkusTransactionException
import io.quarkus.test.QuarkusExtensionTest
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.transaction.RollbackException
import jakarta.transaction.Status
import jakarta.transaction.Synchronization
import jakarta.transaction.TransactionSynchronizationRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

class JpaIdempotencyRegistryTest {
  companion object {
    private val NOW = Instant.parse("2026-01-01T00:00:00Z")

    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest()
      .withApplicationRoot { jar -> jar.addClasses(RegistryTestData::class.java, FixedClock::class.java) }
      .overrideConfigKey("quarkus.datasource.db-kind", "h2")
      .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:registry;DB_CLOSE_DELAY=-1")
      .overrideConfigKey("quarkus.hibernate-orm.schema-management.strategy", "drop-and-create")
      // The extension must add its model even though this package excludes its entity.
      .overrideConfigKey("quarkus.hibernate-orm.packages", "example.application.entities")
  }

  @Inject lateinit var registry: IdempotencyRegistry
  @Inject lateinit var entityManager: EntityManager
  @Inject lateinit var synchronizationRegistry: TransactionSynchronizationRegistry

  @BeforeEach
  fun clearDatabase() {
    QuarkusTransaction.requiringNew().run {
      entityManager.createQuery("delete from WorkerTaskLogEntry").executeUpdate()
    }
  }

  @Test
  fun replacesNoOpDefaultAndDistinguishesMissingFromEmpty() {
    assertThat(registry).isInstanceOf(JpaIdempotencyRegistry::class.java)
    val task = task("empty", "process")
    assertThat(registry.getTaskResult(task)).isNull()
    registry.register(task, emptyMap())
    assertThat(registry.getTaskResult(task)).isEmpty()
    QuarkusTransaction.requiringNew().run {
      assertThat(entityManager.find(TaskLogEntry::class.java, "empty")).isNotNull()
      assertThat(entityManager.createNativeQuery("select result_ from task_log_entry_ where task_id_ = 'empty'")
        .singleResult).isNull()
    }
  }

  @Test
  fun updatesExistingTaskAndTimestampAndProcess() {
    val task = task("updated", "old-process")
    registry.register(task, mapOf("value" to "old"))
    registry.register(task("updated", "new-process"), mapOf("value" to "new"))
    registry.removeAllTaskResults("old-process")
    assertThat(registry.getTaskResult(task)).containsExactlyEntriesOf(mapOf("value" to "new"))
    QuarkusTransaction.requiringNew().run {
      assertThat(entityManager.createQuery("select count(e) from WorkerTaskLogEntry e", Long::class.javaObjectType)
        .singleResult).isEqualTo(1L)
      assertThat(entityManager.createQuery("select e.createdAt from WorkerTaskLogEntry e", Instant::class.java)
        .singleResult).isEqualTo(NOW)
    }
    registry.removeAllTaskResults("new-process")
    assertThat(registry.getTaskResult(task)).isNull()
  }

  @Test
  fun removesSingleAndProcessResultsWhileRetainingUnrelatedResults() {
    registry.register(task("first", "process"), emptyMap())
    registry.register(task("second", "process"), emptyMap())
    registry.register(task("retained", "other"), mapOf("value" to "retained"))
    registry.removeTaskResult("missing")
    registry.removeAllTaskResults("missing")
    registry.removeTaskResult("first")
    assertThat(registry.getTaskResult(task("second", "process"))).isEmpty()
    registry.removeAllTaskResults("process")
    registry.removeAllTaskResults("process")
    assertThat(registry.getTaskResult(task("first", "process"))).isNull()
    assertThat(registry.getTaskResult(task("second", "process"))).isNull()
    assertThat(registry.getTaskResult(task("retained", "other"))).containsEntry("value", "retained")
  }

  @Test
  fun joinsBusinessTransactionAndRollsBackRegistrationAndRemovals() {
    registry.register(task("existing", "process"), mapOf("value" to "before"))
    assertThatThrownBy {
      QuarkusTransaction.requiringNew().run {
        registry.register(task("new", "process"), mapOf("value" to "new"))
        registry.removeTaskResult("existing")
        assertThat(registry.getTaskResult(task("existing", "process"))).isNull()
        assertThat(registry.getTaskResult(task("new", "process"))).containsEntry("value", "new")
        QuarkusTransaction.setRollbackOnly()
        assertThat(registry.getTaskResult(task("new", "process"))).containsEntry("value", "new")
      }
    }.isInstanceOf(QuarkusTransactionException::class.java).hasCauseInstanceOf(RollbackException::class.java)
    assertThat(registry.getTaskResult(task("new", "process"))).isNull()
    assertThat(registry.getTaskResult(task("existing", "process"))).containsEntry("value", "before")
    assertThatThrownBy {
      QuarkusTransaction.requiringNew().run {
        registry.removeAllTaskResults("process")
        QuarkusTransaction.setRollbackOnly()
      }
    }.isInstanceOf(QuarkusTransactionException::class.java).hasCauseInstanceOf(RollbackException::class.java)
    assertThat(registry.getTaskResult(task("existing", "process"))).isNotNull()
  }

  @Test
  fun supportsApplicationControlledRetention() {
    registry.register(task("expired", "process"), mapOf("value" to "old"))
    registry.register(task("retained", "process"), mapOf("value" to "current"))
    val expiredIds = QuarkusTransaction.requiringNew().call {
      entityManager.createQuery("update WorkerTaskLogEntry e set e.createdAt = :old where e.taskId = :id")
        .setParameter("old", NOW.minusSeconds(3600)).setParameter("id", "expired").executeUpdate()
      entityManager.createQuery(
        "select e.taskId from WorkerTaskLogEntry e where e.createdAt < :cutoff", String::class.java
      ).setParameter("cutoff", NOW).resultList
    }
    expiredIds.forEach(registry::removeTaskResult)
    assertThat(registry.getTaskResult(task("expired", "process"))).isNull()
    assertThat(registry.getTaskResult(task("retained", "process"))).containsEntry("value", "current")
  }

  @Test
  fun supportsNonTransactionalWorkerThreads() {
    CompletableFuture.runAsync {
      assertThat(QuarkusTransaction.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION)
      registry.register(task("thread", "process"), mapOf("large" to "x".repeat(4096)))
      assertThat(registry.getTaskResult(task("thread", "process"))).containsKey("large")
      registry.removeTaskResult("thread")
    }.join()
    assertThat(registry.getTaskResult(task("thread", "process"))).isNull()
  }

  @Test
  fun removesResultFromAfterCommitCallback() {
    val failure = AtomicReference<Throwable>()
    QuarkusTransaction.requiringNew().run {
      registry.register(task("after-commit", "process"), mapOf("value" to "cached"))
      synchronizationRegistry.registerInterposedSynchronization(object : Synchronization {
        override fun beforeCompletion() {}

        override fun afterCompletion(status: Int) {
          try {
            assertThat(status).isEqualTo(Status.STATUS_COMMITTED)
            registry.removeTaskResult("after-commit")
          } catch (exception: Throwable) {
            failure.set(exception)
          }
        }
      })
    }
    assertThat(failure.get()).isNull()
    assertThat(registry.getTaskResult(task("after-commit", "process"))).isNull()
  }

  @Test
  fun rejectsMissingProcessMetadataWithoutWriting() {
    assertThatThrownBy { registry.register(TaskInformation("invalid", emptyMap()), emptyMap()) }
      .isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("process instance ID")
    assertThat(registry.getTaskResult(task("invalid", "process"))).isNull()
  }

  @Singleton
  class FixedClock {
    @Produces
    @Singleton
    fun clock(): Clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
  }
}
