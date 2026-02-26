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
    disabledRegistry.register(taskInformation, mapOf("key" to "result"))
    assertThat(disabledRegistry.hasTaskInformation(taskInformation)).isFalse()
  }

  @Test
  fun `should register and find task information`() {
    val taskInformation = TaskInformation(taskId = UUID.randomUUID().toString(), meta = mapOf())

    assertThat(registry.hasTaskInformation(taskInformation)).isFalse()

    registry.register(taskInformation, mapOf("key" to "result"))

    assertThat(registry.hasTaskInformation(taskInformation)).isTrue()
    assertThat(registry.getResult(taskInformation)).isEqualTo(mapOf("key" to "result"))
  }

  @Test
  fun `should return false for unknown task`() {
    val taskInformation = TaskInformation(taskId = UUID.randomUUID().toString(), meta = mapOf())
    assertThat(registry.hasTaskInformation(taskInformation)).isFalse()
  }

  @Test
  fun `should handle empty result map`() {
    val taskInformation = TaskInformation(taskId = UUID.randomUUID().toString(), meta = mapOf())

    registry.register(taskInformation, emptyMap())

    assertThat(registry.hasTaskInformation(taskInformation)).isTrue()
    assertThat(registry.getResult(taskInformation)).isEmpty()
  }

  @Test
  fun `should find task with different but equal task information object`() {
    val taskId = UUID.randomUUID().toString()
    val taskInformation1 = TaskInformation(taskId = taskId, meta = mapOf("foo" to "bar"))
    val taskInformation2 = TaskInformation(taskId = taskId, meta = mapOf("other" to "meta"))

    registry.register(taskInformation1, mapOf("key" to "result"))

    // The default comparator only compares taskId
    assertThat(registry.hasTaskInformation(taskInformation2)).isTrue()
    assertThat(registry.getResult(taskInformation2)).isEqualTo(mapOf("key" to "result"))
  }
}
