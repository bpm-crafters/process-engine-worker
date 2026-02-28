package dev.bpmcrafters.processengine.worker.itest.camunda7.external

import org.springframework.context.annotation.Import

@Import(
  InMemoryIdempotencyRegistryConfiguration::class,
  WorkerWithTransactionalAnnotation::class
)
class IdempotencyWithTransactionTest : AbstractIdempotencyTest()
