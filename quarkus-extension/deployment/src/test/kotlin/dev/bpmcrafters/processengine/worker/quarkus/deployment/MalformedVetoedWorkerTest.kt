package dev.bpmcrafters.processengine.worker.quarkus.deployment

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.quarkus.WorkerConfig
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import io.quarkus.arc.Arc
import io.quarkus.test.QuarkusExtensionTest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Vetoed
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class MalformedVetoedWorkerTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot { it.addClass(InvalidVetoedWorker::class.java) }
  }

  @Inject
  lateinit var config: WorkerConfig

  @Test
  fun doesNotInspectAPrivateWorkerMethodOnAVetoedClass() {
    assertThat(config.enabled()).isTrue()
    assertThat(config.registerProcessWorkers()).isTrue()
    assertThat(Arc.container().instance(InvalidVetoedWorker::class.java).isAvailable).isFalse()
    assertThat(Arc.container().instance(TaskSubscriptionApi::class.java).isAvailable).isFalse()
    assertThat(Arc.container().instance(ServiceTaskCompletionApi::class.java).isAvailable).isFalse()
  }

  @Vetoed
  @ApplicationScoped
  class InvalidVetoedWorker {
    @ProcessEngineWorker
    private fun work(): Map<String, Any?> = emptyMap()
  }
}
