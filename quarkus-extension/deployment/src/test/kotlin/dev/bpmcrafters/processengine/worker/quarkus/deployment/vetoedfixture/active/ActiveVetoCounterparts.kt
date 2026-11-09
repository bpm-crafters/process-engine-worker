package dev.bpmcrafters.processengine.worker.quarkus.deployment.vetoedfixture.active

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Vetoed

@ApplicationScoped
class ActiveSubpackageWorker {
  @ProcessEngineWorker(topic = "active-subpackage")
  fun work(): Map<String, Any?> = mapOf("value" to "active-subpackage")
}

@Vetoed
@ApplicationScoped
open class VetoedWorkerBase {
  @ProcessEngineWorker(topic = "inherited-vetoed-base")
  open fun work(): Map<String, Any?> = mapOf("value" to "inherited-vetoed-base")
}

@ApplicationScoped
class ActiveVetoedBaseSubclass : VetoedWorkerBase()
