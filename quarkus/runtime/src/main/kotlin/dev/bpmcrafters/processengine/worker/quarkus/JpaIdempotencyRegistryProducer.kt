package dev.bpmcrafters.processengine.worker.quarkus

import dev.bpmcrafters.processengine.worker.idempotency.EntityManagerJpaIdempotencyRegistry
import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.transaction.TransactionalExecutor
import io.quarkus.arc.DefaultBean
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import java.util.function.Supplier

/**
 * Produces the JPA-based idempotency registry (`process-engine-worker-idempotency-registry-jpa` on top of
 * `quarkus-hibernate-orm`), using the entity manager of the default persistence unit.
 *
 * The deployment module registers this class instead of the [NoOpIdempotencyRegistryProducer] if Hibernate ORM is enabled
 * and the JPA idempotency module is present, and excludes it from bean discovery otherwise. The bean is a `@DefaultBean`,
 * so an application-provided [IdempotencyRegistry] bean still wins.
 * @since 0.8.6
 */
@Singleton
open class JpaIdempotencyRegistryProducer {

  /**
   * Produces the JPA-based idempotency registry.
   * @param entityManager entity manager of the default persistence unit (resolved lazily to report a clear error if missing).
   * @param transactionalExecutor transactional executor (JTA).
   * @return idempotency registry.
   */
  @Produces
  @ApplicationScoped
  @DefaultBean
  open fun jpaIdempotencyRegistry(entityManager: Instance<EntityManager>, transactionalExecutor: TransactionalExecutor): IdempotencyRegistry {
    check(entityManager.isResolvable) {
      "PROCESS-ENGINE-WORKER-036: The JPA idempotency registry requires the entity manager of the default persistence unit, but none is available. " +
        "Configure a default datasource and persistence unit, or provide an own IdempotencyRegistry bean."
    }
    val defaultEntityManager = entityManager.get()
    return EntityManagerJpaIdempotencyRegistry(Supplier { defaultEntityManager }, transactionalExecutor)
  }
}
