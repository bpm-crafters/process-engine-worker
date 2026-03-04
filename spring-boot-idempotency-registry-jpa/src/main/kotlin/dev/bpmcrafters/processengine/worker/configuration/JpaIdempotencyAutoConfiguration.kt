package dev.bpmcrafters.processengine.worker.configuration

import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.idempotency.JpaIdempotencyRegistry
import jakarta.persistence.EntityManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Autoconfiguration for JPA-based Idempotency Registry.
 * @since 0.8.0
 */
@Configuration
class JpaIdempotencyAutoConfiguration {

  @ConditionalOnMissingBean(IdempotencyRegistry::class)
  @Bean
  fun jpaIdempotencyRegistry(entityManager: EntityManager): IdempotencyRegistry = JpaIdempotencyRegistry(entityManager)

}
