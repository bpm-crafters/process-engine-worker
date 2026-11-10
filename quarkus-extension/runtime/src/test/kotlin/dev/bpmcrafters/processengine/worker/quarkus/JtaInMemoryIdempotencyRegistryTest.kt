package dev.bpmcrafters.processengine.worker.quarkus

import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class JtaInMemoryIdempotencyRegistryTest {
  private val transactions = com.arjuna.ats.jta.TransactionManager.transactionManager()
  private val registry = JtaInMemoryIdempotencyRegistry(
    com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple()
  )
  private val task = TaskInformation("task", mapOf("processInstanceId" to "process"))

  @AfterEach
  fun cleanup() {
    if (transactions.transaction != null) transactions.rollback()
  }

  @Test
  fun nonTransactionalOperationsAreImmediate() {
    registry.register(task, mapOf("value" to 1))
    assertThat(registry.getTaskResult(task)).containsEntry("value", 1)
    registry.removeTaskResult(task.taskId)
    assertThat(registry.getTaskResult(task)).isNull()
    registry.register(task, emptyMap())
    registry.removeAllTaskResults("process")
    assertThat(registry.getTaskResult(task)).isNull()
  }

  @Test
  fun commitPublishesSnapshotAndReadsOwnWrites() {
    transactions.begin()
    val result = hashMapOf<String, Any?>("nullable" to null)
    registry.register(task, result)
    result["later"] = true
    assertThat(registry.getTaskResult(task)).containsEntry("nullable", null).doesNotContainKey("later")
    val suspended = transactions.suspend()
    assertThat(registry.getTaskResult(task)).isNull()
    transactions.resume(suspended)
    transactions.commit()
    assertThat(registry.getTaskResult(task)).containsEntry("nullable", null).doesNotContainKey("later")
  }

  @Test
  fun rollbackNeverPublishesNewResults() {
    transactions.begin()
    registry.register(task, mapOf("value" to 1))
    transactions.rollback()
    assertThat(registry.getTaskResult(task)).isNull()
  }

  @Test
  fun beforeCommitRemovalDoesNotResurrectResult() {
    transactions.begin()
    registry.register(task, mapOf("value" to 1))
    registry.removeTaskResult(task.taskId)
    assertThat(registry.getTaskResult(task)).isNull()
    transactions.commit()
    assertThat(registry.getTaskResult(task)).isNull()
  }

  @Test
  fun rollbackPreservesExistingResultAndRemovalReadIsStaged() {
    registry.register(task, mapOf("value" to 1))
    transactions.begin()
    registry.removeTaskResult(task.taskId)
    assertThat(registry.getTaskResult(task)).isNull()
    transactions.rollback()
    assertThat(registry.getTaskResult(task)).containsEntry("value", 1)
  }

  @Test
  fun processRemovalAndLaterWritesRetainOperationOrdering() {
    val other = TaskInformation("other-task", mapOf("processInstanceId" to "other-process"))
    registry.register(task, mapOf("old" to true))
    transactions.begin()
    registry.register(task, mapOf("intermediate" to true))
    registry.register(other, mapOf("other" to true))
    registry.removeAllTaskResults("process")
    assertThat(registry.getTaskResult(task)).isNull()
    registry.register(task, mapOf("new" to true))
    assertThat(registry.getTaskResult(task)).containsOnlyKeys("new")
    transactions.commit()
    assertThat(registry.getTaskResult(task)).containsOnlyKeys("new")
    assertThat(registry.getTaskResult(other)).containsEntry("other", true)
  }
}
