package dev.bpmcrafters.processengine.worker.acceptance

import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class GenericPayloadWorker : GenericWorker<WorkerPayload> {
  override fun execute(payload: WorkerPayload): Map<String, Any?> =
    mapOf("generic" to payload.name, "nestedAmount" to payload.details.amount)
}
