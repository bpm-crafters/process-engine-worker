package dev.bpmcrafters.processengine.worker.quarkus

import dev.bpmcrafters.processengine.worker.registrar.ProcessEngineWorkerMetrics
import dev.bpmcrafters.processengine.worker.registrar.metrics.ProcessEngineWorkerMetricsMicrometer
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.arc.DefaultBean

/**
 * Metrics implementation recording the worker metrics in the Micrometer registry of Quarkus.
 *
 * This class carries no bean-defining annotation on purpose: it is registered as a `@Singleton` bean by the deployment
 * module only if the Micrometer metrics capability is present (it must not be discovered from the Jandex index otherwise).
 * It is a `@DefaultBean`, so an application-provided [ProcessEngineWorkerMetrics] bean wins.
 *
 * @param meterRegistry meter registry.
 * @since 0.8.6
 */
@DefaultBean
open class MicrometerProcessEngineWorkerMetrics(
  meterRegistry: MeterRegistry
) : ProcessEngineWorkerMetrics by ProcessEngineWorkerMetricsMicrometer(meterRegistry)
