package dev.bpmcrafters.processengine.worker.idempotency

import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*

class InMemoryIdempotencyRegistryTest {

  private val registry = InMemoryIdempotencyRegistry(true)

  @Test
  fun `should not find task information if disabled by property`() {
    val disabledRegistry = InMemoryIdempotencyRegistry(false)
    val taskInformation = TaskInformation(taskId = UUID.randomUUID().toString(), meta = mapOf())
    disabledRegistry.register(taskInformation, "result")
    assertThat(disabledRegistry.hasTaskInformation(taskInformation)).isFalse()
  }

  @Test
  fun `should register and find task information`() {
    val taskInformation = TaskInformation(taskId = UUID.randomUUID().toString(), meta = mapOf())

    assertThat(registry.hasTaskInformation(taskInformation)).isFalse()

    registry.register(taskInformation, "result")

    assertThat(registry.hasTaskInformation(taskInformation)).isTrue()
    assertThat(registry.getResult(taskInformation)).isEqualTo("result")
  }

  @Test
  fun `should return false for unknown task`() {
    val taskInformation = TaskInformation(taskId = UUID.randomUUID().toString(), meta = mapOf())
    assertThat(registry.hasTaskInformation(taskInformation)).isFalse()
  }

  @Test
  fun `should handle null result`() {
    val taskInformation = TaskInformation(taskId = UUID.randomUUID().toString(), meta = mapOf())

    registry.register(taskInformation, null)

    assertThat(registry.hasTaskInformation(taskInformation)).isTrue()
    assertThat(registry.getResult(taskInformation)).isNull()
  }

  @Test
  fun `should find task with different but equal task information object`() {
    val taskId = UUID.randomUUID().toString()
    val taskInformation1 = TaskInformation(taskId = taskId, meta = mapOf("foo" to "bar"))
    val taskInformation2 = TaskInformation(taskId = taskId, meta = mapOf("other" to "meta"))

    registry.register(taskInformation1, "result")

    // The default comparator only compares taskId
    assertThat(registry.hasTaskInformation(taskInformation2)).isTrue()
    assertThat(registry.getResult(taskInformation2)).isEqualTo("result")
  }
}
