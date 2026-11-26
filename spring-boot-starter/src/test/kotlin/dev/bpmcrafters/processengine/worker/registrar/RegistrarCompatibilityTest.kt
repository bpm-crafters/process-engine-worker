package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class RegistrarCompatibilityTest {
  @Test
  fun `legacy nested types remain usable from Kotlin`() {
    val handler = ProcessEngineStarterRegistrar.TaskHandlerWithResult { task, payload -> payload[task.taskId] }
    val task = TaskInformation("task", emptyMap())
    assertThat(handler(task, mapOf("task" to "result"))).isEqualTo("result")
    val retry = ProcessEngineStarterRegistrar.FailureRetry(3, Duration.ofSeconds(1))
    val (count, backoff) = retry.copy(retryCount = 2)
    assertThat(count).isEqualTo(2)
    assertThat(backoff).isEqualTo(Duration.ofSeconds(1))
  }
}
