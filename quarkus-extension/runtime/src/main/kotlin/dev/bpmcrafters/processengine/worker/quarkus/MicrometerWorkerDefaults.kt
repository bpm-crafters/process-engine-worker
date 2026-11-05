package dev.bpmcrafters.processengine.worker.quarkus

import dev.bpmcrafters.processengine.worker.registrar.ProcessEngineWorkerMetrics
import dev.bpmcrafters.processengine.worker.registrar.metrics.ProcessEngineWorkerMetricsMicrometer
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.arc.DefaultBean
import jakarta.annotation.Priority
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton

/** Added to Arc only when the Micrometer extension is present. */
@Singleton
class MicrometerWorkerDefaults {
  @Produces @Singleton @DefaultBean @Priority(10)
  fun metrics(registry: MeterRegistry): ProcessEngineWorkerMetrics = ProcessEngineWorkerMetricsMicrometer(registry)
}
