package dev.bpmcrafters.processengine.worker.fixture

import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.idempotency.InMemoryIdempotencyRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class InMemoryIdempotencyRegistryConfiguration {
  @Bean
  @Primary
  fun inMemIdempotencyRegistry(): IdempotencyRegistry = InMemoryIdempotencyRegistry()
}
