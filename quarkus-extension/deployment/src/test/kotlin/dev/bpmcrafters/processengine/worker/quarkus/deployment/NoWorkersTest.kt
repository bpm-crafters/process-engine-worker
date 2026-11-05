package dev.bpmcrafters.processengine.worker.quarkus.deployment

import dev.bpmcrafters.processengine.worker.quarkus.WorkerConfig
import io.quarkus.test.QuarkusExtensionTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class NoWorkersTest {
  companion object {
    @JvmField @RegisterExtension val APP = QuarkusExtensionTest().withEmptyApplication()
  }
  @Inject lateinit var config: WorkerConfig
  @Test fun startsWithoutEngineClientsWhenNoWorkersExist() {
    assertThat(config.enabled()).isTrue()
    assertThat(config.registerProcessWorkers()).isTrue()
    assertThat(config.completeTasksBeforeCommit()).isFalse()
    assertThat(config.removeTaskResultOnCompletion()).isTrue()
    assertThat(config.tenantId()).isEmpty()
  }
}
