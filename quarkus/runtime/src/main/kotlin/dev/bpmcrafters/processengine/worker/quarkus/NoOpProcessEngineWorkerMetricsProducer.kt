package dev.bpmcrafters.processengine.worker.quarkus

import dev.bpmcrafters.processengine.worker.registrar.ProcessEngineWorkerMetrics
import dev.bpmcrafters.processengine.worker.registrar.metrics.ProcessEngineWorkerMetricsNoOp
import io.quarkus.arc.DefaultBean
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton

/**
 * Produces the default metrics doing nothing. Registered by the deployment module only if Micrometer metrics
 * (`quarkus-micrometer`) are missing, otherwise [MicrometerProcessEngineWorkerMetrics] is registered; excluded from
 * bean discovery if not registered.
 * @since 0.8.6
 */
@Singleton
open class NoOpProcessEngineWorkerMetricsProducer {

  /**
   * Produces the no-op metrics.
   * @return metrics.
   */
  @Produces
  @ApplicationScoped
  @DefaultBean
  open fun noOpProcessEngineWorkerMetrics(): ProcessEngineWorkerMetrics = ProcessEngineWorkerMetricsNoOp
}
