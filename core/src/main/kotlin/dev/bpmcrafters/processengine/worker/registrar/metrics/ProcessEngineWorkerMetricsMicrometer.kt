package dev.bpmcrafters.processengine.worker.registrar.metrics

import dev.bpmcrafters.processengine.worker.registrar.ProcessEngineWorkerMetrics
import io.micrometer.core.instrument.MeterRegistry

/**
 * Metrics implementation recording the metrics in micrometer registry.
 */
class ProcessEngineWorkerMetricsMicrometer(
  private val meterRegistry: MeterRegistry
) : ProcessEngineWorkerMetrics {

  companion object {
    const val PREFIX = "bpmcrafters.worker.external.tasks"
    const val TOPIC = "topic"
  }

  override fun taskReceived(topic: String) {
    meterRegistry.counter("${PREFIX}.received", TOPIC, topic).increment()
  }

  override fun taskCompletedByError(topic: String) {
    meterRegistry.counter("${PREFIX}.bpmn.error", TOPIC, topic).increment()
  }

  override fun taskCompleted(topic: String) {
    meterRegistry.counter("${PREFIX}.completed", TOPIC, topic).increment()
  }

  override fun taskFailed(topic: String) {
    meterRegistry.counter("${PREFIX}.failed", TOPIC, topic).increment()
  }
}
