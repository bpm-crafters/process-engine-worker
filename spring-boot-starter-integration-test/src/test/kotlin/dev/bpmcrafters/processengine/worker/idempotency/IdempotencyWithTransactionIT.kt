package dev.bpmcrafters.processengine.worker.idempotency

import dev.bpmcrafters.processengine.worker.idempotency.AbstractIdempotencyIT
import dev.bpmcrafters.processengine.worker.itest.WorkerWithTransactionalAnnotation
import org.springframework.context.annotation.Import

@Import(
  InMemoryIdempotencyRegistryConfiguration::class,
  WorkerWithTransactionalAnnotation::class
)
class IdempotencyWithTransactionIT : AbstractIdempotencyIT()
