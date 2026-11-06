package dev.bpmcrafters.processengine.worker.quarkus.deployment

import dev.bpmcrafters.processengine.worker.registrar.ProcessEngineWorkerMetrics
import dev.bpmcrafters.processengine.worker.registrar.metrics.ProcessEngineWorkerMetricsMicrometer
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.quarkus.test.QuarkusExtensionTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class MicrometerActivationTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest().withEmptyApplication()
  }

  @Inject
  lateinit var metrics: ProcessEngineWorkerMetrics
  @Inject
  lateinit var registry: MeterRegistry

  @Test
  fun augmentationSelectsMicrometerDefaultWhenTheExtensionIsPresent() {
    assertThat(metrics).isInstanceOf(ProcessEngineWorkerMetricsMicrometer::class.java)
    val backend = SimpleMeterRegistry()
    val composite = registry as CompositeMeterRegistry
    composite.add(backend)
    try {
      metrics.taskReceived("augmentation-topic")
      assertThat(backend.get("bpmcrafters.worker.external.tasks.received").tag("topic", "augmentation-topic").counter().count()).isEqualTo(1.0)
    } finally {
      composite.remove(backend)
      backend.close()
    }
  }
}
