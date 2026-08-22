package dev.bpmcrafters.processengine.worker.quarkus

import com.fasterxml.jackson.databind.ObjectMapper
import dev.bpmcrafters.processengine.worker.registrar.JacksonVariableConverter
import dev.bpmcrafters.processengine.worker.registrar.ParameterResolver
import dev.bpmcrafters.processengine.worker.registrar.ResultResolver
import dev.bpmcrafters.processengine.worker.registrar.VariableConverter
import io.quarkus.arc.DefaultBean
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton

/**
 * Default beans of the process engine worker. Every producer is a `@DefaultBean`, so an application can replace
 * any of them by providing an own bean of the same type (e.g. via `@Produces`).
 *
 * The defaults depending on the presence of other extensions are selected by the deployment module:
 * [dev.bpmcrafters.processengine.worker.transaction.TransactionalExecutor] ([NoneTransactionalExecutorProducer] or
 * [JtaTransactionalExecutor]), [dev.bpmcrafters.processengine.worker.registrar.ProcessEngineWorkerMetrics]
 * ([NoOpProcessEngineWorkerMetricsProducer] or [MicrometerProcessEngineWorkerMetrics]) and
 * [dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry] ([NoOpIdempotencyRegistryProducer] or
 * [JpaIdempotencyRegistryProducer]).
 * @since 0.8.6
 */
@Singleton
open class ProcessEngineWorkerProducers {

  /**
   * Default variable converter based on the Quarkus Jackson object mapper.
   * @param objectMapper object mapper.
   * @return variable converter.
   */
  @Produces
  @ApplicationScoped
  @DefaultBean
  open fun variableConverter(objectMapper: ObjectMapper): VariableConverter = JacksonVariableConverter(objectMapper)

  /**
   * Default parameter resolver with the built-in resolution strategies.
   * @return parameter resolver.
   */
  @Produces
  @ApplicationScoped
  @DefaultBean
  open fun parameterResolver(): ParameterResolver = ParameterResolver.builder().build()

  /**
   * Default result resolver with the built-in resolution strategies.
   * @return result resolver.
   */
  @Produces
  @ApplicationScoped
  @DefaultBean
  open fun resultResolver(): ResultResolver = ResultResolver.builder().build()

}
