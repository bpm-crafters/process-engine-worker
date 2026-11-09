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

class ClassVetoedWorkerTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot { it.addClass(VetoedWorker::class.java) }
  }

  @Inject
  lateinit var config: WorkerConfig

  @Test
  fun startsWithWorkersEnabledAndNoEngineClientsWhenTheWorkerIsVetoed() {
    assertThat(config.enabled()).isTrue()
    assertThat(config.registerProcessWorkers()).isTrue()
    assertThat(Arc.container().instance(VetoedWorker::class.java).isAvailable).isFalse()
    assertThat(Arc.container().instance(TaskSubscriptionApi::class.java).isAvailable).isFalse()
    assertThat(Arc.container().instance(ServiceTaskCompletionApi::class.java).isAvailable).isFalse()
  }

  @Vetoed
  @ApplicationScoped
  class VetoedWorker {
    @ProcessEngineWorker(topic = "class-vetoed")
    fun work(): Map<String, Any?> = emptyMap()
  }
}
