package dev.bpmcrafters.processengine.worker.itest.camunda7.external

import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.idempotency.InMemoryIdempotencyRegistry
import org.springframework.context.annotation.Bean

class InMemoryIdempotencyRegistryConfiguration {

  @Bean
  fun idempotencyRegistry(): IdempotencyRegistry = InMemoryIdempotencyRegistry()

}
