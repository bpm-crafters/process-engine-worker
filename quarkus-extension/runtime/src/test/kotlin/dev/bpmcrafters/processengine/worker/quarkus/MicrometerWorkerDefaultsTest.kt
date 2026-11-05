package dev.bpmcrafters.processengine.worker.quarkus

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test

class MicrometerWorkerDefaultsTest {
  @Test
  fun preservesExistingMeterNamesAndTopicTags() {
    val registry = SimpleMeterRegistry()
    try {
      val metrics = MicrometerWorkerDefaults().metrics(registry)
      metrics.taskReceived("topic")
      metrics.taskCompleted("topic")
      metrics.taskCompletedByError("topic")
      metrics.taskFailed("topic")
      for (suffix in listOf("received", "completed", "bpmn.error", "failed")) {
        assertThat(registry.get("bpmcrafters.worker.external.tasks.$suffix").tag("topic", "topic").counter().count())
          .isEqualTo(1.0)
      }
    } finally {
      registry.close()
    }
  }
}
