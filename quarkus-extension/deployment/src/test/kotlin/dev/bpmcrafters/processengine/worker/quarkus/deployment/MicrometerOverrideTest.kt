package dev.bpmcrafters.processengine.worker.quarkus.deployment

import dev.bpmcrafters.processengine.worker.registrar.ProcessEngineWorkerMetrics
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.test.QuarkusExtensionTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class MicrometerOverrideTest {
  companion object {
    @JvmField @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot { it.addClass(CustomMetrics::class.java) }
  }
  @Inject lateinit var metrics: ProcessEngineWorkerMetrics
  @Inject lateinit var registry: MeterRegistry

  @Test fun applicationMetricsOverrideBothDefaultsEvenWithMicrometerInstalled() {
    metrics.taskReceived("override-topic")
    assertThat(metrics).isInstanceOf(CustomMetrics::class.java)
    assertThat((metrics as CustomMetrics).received).isEqualTo(1)
    assertThat(registry.find("bpmcrafters.worker.external.tasks.received").tag("topic", "override-topic").counter()).isNull()
  }

  @Singleton
  class CustomMetrics : ProcessEngineWorkerMetrics {
    var received = 0
    override fun taskReceived(topic: String) { received++ }
    override fun taskCompleted(topic: String) {}
    override fun taskCompletedByError(topic: String) {}
    override fun taskFailed(topic: String) {}
  }
}
