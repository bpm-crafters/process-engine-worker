package dev.bpmcrafters.processengine.worker.registrar.metrics

import dev.bpmcrafters.processengine.worker.registrar.ProcessEngineWorkerMetrics
import io.micrometer.core.instrument.MeterRegistry

/**
 * Metrics implementation recording the metrics in micrometer registry.
 * @since 0.6.1
 **/
class ProcessEngineWorkerMetricsMicrometer(
  private val meterRegistry: MeterRegistry
) : ProcessEngineWorkerMetrics {

  /**
   * Meter name prefix and tag keys.
   * @since 0.6.1
   **/
  companion object {
    /**
     * Prefix shared by worker counter names.
     **/
    const val PREFIX = "bpmcrafters.worker.external.tasks"
    /**
     * Tag key for the worker topic.
     **/
    const val TOPIC = "topic"
  }

  /**
   * Increments the received counter for the topic.
   **/
  override fun taskReceived(topic: String) {
    meterRegistry.counter("${PREFIX}.received", TOPIC, topic).increment()
  }

  /**
   * Increments the BPMN error counter for the topic.
   **/
  override fun taskCompletedByError(topic: String) {
    meterRegistry.counter("${PREFIX}.bpmn.error", TOPIC, topic).increment()
  }

  /**
   * Increments the successful completion counter for the topic.
   **/
  override fun taskCompleted(topic: String) {
    meterRegistry.counter("${PREFIX}.completed", TOPIC, topic).increment()
  }

  /**
   * Increments the failure counter for the topic.
   **/
  override fun taskFailed(topic: String) {
    meterRegistry.counter("${PREFIX}.failed", TOPIC, topic).increment()
  }
}
