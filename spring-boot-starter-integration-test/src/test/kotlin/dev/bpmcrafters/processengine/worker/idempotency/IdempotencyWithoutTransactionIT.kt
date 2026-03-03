package dev.bpmcrafters.processengine.worker.idempotency

import dev.bpmcrafters.processengine.worker.idempotency.AbstractIdempotencyIT
import dev.bpmcrafters.processengine.worker.itest.WorkerWithoutTransactionalAnnotation
import org.springframework.context.annotation.Import

@Import(
  InMemoryIdempotencyRegistryConfiguration::class,
  WorkerWithoutTransactionalAnnotation::class
)
class IdempotencyWithoutTransactionIT : AbstractIdempotencyIT()
