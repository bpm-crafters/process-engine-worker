package dev.bpmcrafters.processengine.worker.idempotency

import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.CommonRestrictions.PROCESS_INSTANCE_ID
import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*

class InMemoryIdempotencyRegistryTest {

  private val registry = InMemoryIdempotencyRegistry()

  @Test
  fun `should register result`() {
    val taskInformation = TaskInformation(
      taskId = UUID.randomUUID().toString(),
      meta = mapOf(PROCESS_INSTANCE_ID to UUID.randomUUID().toString())
    )
    assertThat(registry.getTaskResult(taskInformation)).isNull()

    val result = mapOf("A" to "the B")
    registry.register(taskInformation, result)

    assertThat(registry.getTaskResult(taskInformation)).isSameAs(result)
  }

}
