package dev.bpmcrafters.processengine.worker.registrar.metrics

import dev.bpmcrafters.processengine.worker.registrar.ProcessEngineWorkerMetrics

/**
 * A metrics implementation doing nothing in case no micrometer is available.
 */
object ProcessEngineWorkerMetricsNoOp : ProcessEngineWorkerMetrics {
  override fun taskReceived(topic: String) {}

  override fun taskCompletedByError(topic: String) {}

  override fun taskCompleted(topic: String) {}

  override fun taskFailed(topic: String) {}
}
