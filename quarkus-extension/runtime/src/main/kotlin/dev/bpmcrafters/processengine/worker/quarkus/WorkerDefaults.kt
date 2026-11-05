package dev.bpmcrafters.processengine.worker.quarkus

import com.fasterxml.jackson.databind.ObjectMapper
import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.idempotency.NoOpIdempotencyRegistry
import dev.bpmcrafters.processengine.worker.registrar.*
import dev.bpmcrafters.processengine.worker.registrar.metrics.ProcessEngineWorkerMetricsNoOp
import io.quarkus.arc.DefaultBean
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton

@Singleton
class WorkerDefaults {
  @Produces @Singleton @DefaultBean
  fun converter(mapper: ObjectMapper): VariableConverter = JacksonVariableConverter(mapper)

  @Produces @Singleton @DefaultBean
  fun parameters(): ParameterResolver = ParameterResolver.builder().build()

  @Produces @Singleton @DefaultBean
  fun results(): ResultResolver = ResultResolver.builder().build()

  @Produces @Singleton @DefaultBean
  fun idempotency(): IdempotencyRegistry = NoOpIdempotencyRegistry()

  @Produces @Singleton @DefaultBean
  fun metrics(): ProcessEngineWorkerMetrics = ProcessEngineWorkerMetricsNoOp
}
