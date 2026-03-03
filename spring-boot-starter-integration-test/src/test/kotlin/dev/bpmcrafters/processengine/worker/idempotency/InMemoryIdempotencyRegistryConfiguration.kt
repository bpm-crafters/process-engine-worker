package dev.bpmcrafters.processengine.worker.idempotency

import org.springframework.context.annotation.Bean

class InMemoryIdempotencyRegistryConfiguration {

  @Bean
  fun idempotencyRegistry(): IdempotencyRegistry = InMemoryIdempotencyRegistry()

}
