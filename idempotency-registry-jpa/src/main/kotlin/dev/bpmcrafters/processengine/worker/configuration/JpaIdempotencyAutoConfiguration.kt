package dev.bpmcrafters.processengine.worker.configuration

import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.idempotency.JpaIdempotencyRegistry
import jakarta.persistence.EntityManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * @since 0.8.0
 */
@Configuration
class JpaIdempotencyAutoConfiguration {

  @Bean
  fun idempotencyRegistry(entityManager: EntityManager): IdempotencyRegistry = JpaIdempotencyRegistry(entityManager)

}
