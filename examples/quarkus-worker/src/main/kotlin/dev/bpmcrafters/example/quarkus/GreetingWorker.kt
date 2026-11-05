package dev.bpmcrafters.example.quarkus

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class GreetingWorker {
  @ProcessEngineWorker("greet")
  fun greet(@Variable("request") request: GreetingRequest): Map<String, Any?> =
    mapOf("greeting" to "Hello ${request.name}")
}
