package dev.bpmcrafters.processengine.worker.quarkus

import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Optional

class ProcessEngineWorkerConfigTest {

  private fun config(
    enabled: Boolean = true,
    completeTasksBeforeCommit: Boolean = false,
    removeTaskResultOnCompletion: Boolean = true,
    tenantId: String? = null
  ) = object : ProcessEngineWorkerConfig {
    override fun enabled(): Boolean = enabled
    override fun completeTasksBeforeCommit(): Boolean = completeTasksBeforeCommit
    override fun removeTaskResultOnCompletion(): Boolean = removeTaskResultOnCompletion
    override fun tenantId(): Optional<String> = Optional.ofNullable(tenantId)
  }

  @Test
  fun `maps defaults`() {
    assertThat(config().toConfiguration()).isEqualTo(ProcessEngineWorkerConfiguration())
  }

  @Test
  fun `maps values`() {
    assertThat(config(completeTasksBeforeCommit = true, removeTaskResultOnCompletion = false, tenantId = "tenant").toConfiguration())
      .isEqualTo(ProcessEngineWorkerConfiguration(completeTasksBeforeCommit = true, removeTaskResultOnCompletion = false, tenantId = "tenant"))
  }

  @Test
  fun `maps blank tenant to null`() {
    assertThat(config(tenantId = " ").toConfiguration().tenantId).isNull()
  }

  @Test
  fun `prefix matches spring configuration`() {
    assertThat(ProcessEngineWorkerConfiguration.DEFAULT_PREFIX).isEqualTo("dev.bpm-crafters.process-api.worker")
    assertThat(ProcessEngineWorkerConfiguration.ENABLED_PROPERTY).isEqualTo("dev.bpm-crafters.process-api.worker.enabled")
  }
}
