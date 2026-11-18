package dev.bpmcrafters.processengine.worker.quarkus.deployment.vetoedfixture

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import jakarta.enterprise.context.ApplicationScoped

// No class-level veto: this fixture must be excluded by the archived package-info alone.
@ApplicationScoped
class PackageVetoedWorker {
  @ProcessEngineWorker(topic = "package-vetoed")
  fun work(): Map<String, Any?> = emptyMap()
}
