package dev.bpmcrafters.processengine.worker.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.task.TaskInformation
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.Persistence
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Framework-free test of the [EntityManagerJpaIdempotencyRegistry] using Hibernate ORM and H2.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EntityManagerJpaIdempotencyRegistryTest {

  private lateinit var emf: EntityManagerFactory
  private lateinit var executor: ResourceLocalTransactionalExecutor
  private lateinit var registry: EntityManagerJpaIdempotencyRegistry
  private val now = Instant.parse("2025-01-01T10:00:00Z")

  @BeforeAll
  fun createFactory() {
    emf = Persistence.createEntityManagerFactory("idempotency-test")
  }

  @AfterAll
  fun closeFactory() {
    emf.close()
  }

  @BeforeEach
  fun setUp() {
    executor = ResourceLocalTransactionalExecutor(emf)
    registry = EntityManagerJpaIdempotencyRegistry(executor::currentEntityManager, executor, Clock.fixed(now, ZoneOffset.UTC))
  }

  @AfterEach
  fun tearDown() {
    TaskResultMapSerializer.DEFAULT = JavaTaskResultMapSerializer
    assertThat(executor.isTransactionActive()).isFalse()
    executor.executeInTransaction { executor.currentEntityManager().createQuery("delete from TaskLogEntry").executeUpdate() }
  }

  @Test
  fun `schema matches the documented liquibase changeset`() {
    @Suppress("UNCHECKED_CAST")
    val columns = executor.executeInTransaction {
      executor.currentEntityManager().createNativeQuery(
        "select lower(column_name) from information_schema.columns where lower(table_name) = 'task_log_entry_'"
      ).resultList as List<String>
    }
    assertThat(columns).containsExactlyInAnyOrder("task_id_", "process_instance_id_", "created_at_", "result_")
  }

  @Test
  fun `register and get task result round trip`() {
    val task = task("task-1", "pi-1")
    val result = mapOf("amount" to 42, "name" to "order", "nested" to listOf("a", "b"))

    registry.register(task, result)

    assertThat(registry.getTaskResult(task)).isEqualTo(result)
    val entry = find("task-1")
    assertThat(entry.processInstanceId).isEqualTo("pi-1")
    assertThat(entry.createdAt).isEqualTo(now)
  }

  @Test
  fun `empty result is stored as null column and read as empty map`() {
    val task = task("task-empty", "pi-1")

    registry.register(task, mapOf())

    assertThat(registry.getTaskResult(task)).isNotNull.isEmpty()
    val raw = rawResult("task-empty")
    assertThat(raw).isNull()
  }

  @Test
  fun `unknown task returns null`() {
    assertThat(registry.getTaskResult(task("unknown", "pi-1"))).isNull()
  }

  @Test
  fun `register twice for the same task id merges the entry`() {
    val task = task("task-merge", "pi-1")

    registry.register(task, mapOf("v" to 1))
    registry.register(task, mapOf("v" to 2))

    assertThat(registry.getTaskResult(task)).isEqualTo(mapOf("v" to 2))
    assertThat(count()).isEqualTo(1L)
  }

  @Test
  fun `remove task result deletes the row`() {
    registry.register(task("task-a", "pi-1"), mapOf("v" to 1))
    registry.register(task("task-b", "pi-1"), mapOf("v" to 2))

    registry.removeTaskResult("task-a")

    assertThat(registry.getTaskResult(task("task-a", "pi-1"))).isNull()
    assertThat(registry.getTaskResult(task("task-b", "pi-1"))).isEqualTo(mapOf("v" to 2))
  }

  @Test
  fun `remove all task results by process instance`() {
    registry.register(task("task-a", "pi-1"), mapOf("v" to 1))
    registry.register(task("task-b", "pi-1"), mapOf("v" to 2))
    registry.register(task("task-c", "pi-2"), mapOf("v" to 3))

    registry.removeAllTaskResults("pi-1")

    assertThat(count()).isEqualTo(1L)
    assertThat(registry.getTaskResult(task("task-c", "pi-2"))).isEqualTo(mapOf("v" to 3))
  }

  @Test
  fun `rollback of surrounding transaction leaves no row`() {
    val task = task("task-rollback", "pi-1")

    assertThatThrownBy {
      executor.executeInTransaction {
        registry.register(task, mapOf("v" to 1))
        throw IllegalStateException("worker failed")
      }
    }.isInstanceOf(IllegalStateException::class.java).hasMessage("worker failed")

    assertThat(executor.isTransactionActive()).isFalse()
    assertThat(count()).isEqualTo(0L)
    assertThat(registry.getTaskResult(task)).isNull()
  }

  @Test
  fun `register joins surrounding transaction and is visible after commit`() {
    val task = task("task-join", "pi-1")
    var afterCommitCalled = false

    executor.executeInTransaction {
      registry.register(task, mapOf("v" to 1))
      executor.afterCommitOrNow { afterCommitCalled = true }
      assertThat(afterCommitCalled).isFalse()
    }

    assertThat(afterCommitCalled).isTrue()
    assertThat(registry.getTaskResult(task)).isEqualTo(mapOf("v" to 1))
  }

  @Test
  fun `missing process instance id fails`() {
    assertThatThrownBy {
      registry.register(TaskInformation("task-no-pi", mapOf()), mapOf("v" to 1))
    }.isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("task-no-pi")
    assertThat(executor.isTransactionActive()).isFalse()
    assertThat(count()).isEqualTo(0L)
  }

  @Test
  fun `jackson serializer round trip via converter`() {
    TaskResultMapSerializer.DEFAULT = JacksonTaskResultMapSerializer(ObjectMapper())
    val task = task("task-jackson", "pi-1")
    val result = mapOf("amount" to 42, "name" to "order", "flag" to true, "nested" to mapOf("k" to "v"))

    registry.register(task, result)

    assertThat(registry.getTaskResult(task)).isEqualTo(result)
    val raw = rawResult("task-jackson") as java.sql.Blob
    assertThat(String(raw.getBytes(1, raw.length().toInt()))).startsWith("{").contains("\"amount\":42")
  }

  private fun task(taskId: String, processInstanceId: String) =
    TaskInformation(taskId, mapOf(CommonRestrictions.PROCESS_INSTANCE_ID to processInstanceId))

  private fun find(taskId: String): TaskLogEntry = executor.executeInTransaction {
    requireNotNull(executor.currentEntityManager().find(TaskLogEntry::class.java, taskId))
  }

  private fun count(): Long = executor.executeInTransaction {
    executor.currentEntityManager().createQuery("select count(e) from TaskLogEntry e", java.lang.Long::class.java).singleResult.toLong()
  }

  private fun rawResult(taskId: String): Any? = executor.executeInTransaction {
    executor.currentEntityManager().createNativeQuery("select result_ from task_log_entry_ where task_id_ = :id").setParameter("id", taskId).singleResult
  }
}
