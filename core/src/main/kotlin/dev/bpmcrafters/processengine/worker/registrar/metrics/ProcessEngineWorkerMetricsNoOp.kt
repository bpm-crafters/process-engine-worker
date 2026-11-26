package dev.bpmcrafters.processengine.worker.registrar.metrics

import dev.bpmcrafters.processengine.worker.registrar.ProcessEngineWorkerMetrics

/**
 * A metrics implementation doing nothing in case no micrometer is available.
 * @since 0.6.1
 **/
object ProcessEngineWorkerMetricsNoOp : ProcessEngineWorkerMetrics {

  /**
   * Ignores a task receipt event.
   **/
  override fun taskReceived(topic: String) {}

  /**
   * Ignores a BPMN error completion event.
   **/
  override fun taskCompletedByError(topic: String) {}

  /**
   * Ignores a successful task completion event.
   **/
  override fun taskCompleted(topic: String) {}

  /**
   * Ignores a task failure event.
   **/
  override fun taskFailed(topic: String) {}
}
