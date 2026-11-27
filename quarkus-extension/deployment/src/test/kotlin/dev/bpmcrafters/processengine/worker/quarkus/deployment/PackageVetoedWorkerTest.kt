package dev.bpmcrafters.processengine.worker.quarkus.deployment

import dev.bpmcrafters.processengine.worker.quarkus.WorkerConfig
import dev.bpmcrafters.processengine.worker.quarkus.deployment.vetoedfixture.PackageVetoedWorker
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import io.quarkus.arc.Arc
import io.quarkus.test.QuarkusExtensionTest
import jakarta.enterprise.inject.Vetoed
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class PackageVetoedWorkerTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot {
      it.addClass(PackageVetoedWorker::class.java)
        .addClass("dev.bpmcrafters.processengine.worker.quarkus.deployment.vetoedfixture.package-info")
    }
  }

  @Inject
  lateinit var config: WorkerConfig

  @Test
  fun startsWithoutEngineClientsWhenTheIndexedPackageVetoesTheWorker() {
    assertThat(config.enabled()).isTrue()
    assertThat(config.registerProcessWorkers()).isTrue()
    assertThat(PackageVetoedWorker::class.java.isAnnotationPresent(Vetoed::class.java)).isFalse()
    assertThat(PackageVetoedWorker::class.java.`package`.isAnnotationPresent(Vetoed::class.java)).isTrue()
    assertThat(Arc.container().instance(PackageVetoedWorker::class.java).isAvailable).isFalse()
    assertThat(Arc.container().instance(TaskSubscriptionApi::class.java).isAvailable).isFalse()
    assertThat(Arc.container().instance(ServiceTaskCompletionApi::class.java).isAvailable).isFalse()
  }
}
