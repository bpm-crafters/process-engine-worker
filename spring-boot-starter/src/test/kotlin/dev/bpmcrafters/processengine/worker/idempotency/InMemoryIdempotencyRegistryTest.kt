package dev.bpmcrafters.processengine.worker.idempotency

import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*

class InMemoryIdempotencyRegistryTest {

  private val registry = InMemoryIdempotencyRegistry()

  @Test
  fun `should register result`() {
    val taskId = TaskInformation(taskId = UUID.randomUUID().toString(), meta = mapOf())
    assertThat(registry.getTaskResult(taskId)).isNull()

    val result = mapOf("A" to "the B")
    registry.register(taskId, result)

    assertThat(registry.getTaskResult(taskId)).isSameAs(result)
  }

}
