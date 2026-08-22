package dev.bpmcrafters.processengine.worker.quarkus

import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.idempotency.NoOpIdempotencyRegistry
import io.quarkus.arc.DefaultBean
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton

/**
 * Produces the default idempotency registry doing nothing. Registered by the deployment module unless the JPA-based
 * registry is active (see [JpaIdempotencyRegistryProducer]); excluded from bean discovery otherwise.
 * @since 0.8.6
 */
@Singleton
open class NoOpIdempotencyRegistryProducer {

  /**
   * Produces the no-op idempotency registry.
   * @return idempotency registry.
   */
  @Produces
  @ApplicationScoped
  @DefaultBean
  open fun noOpIdempotencyRegistry(): IdempotencyRegistry = NoOpIdempotencyRegistry()
}
