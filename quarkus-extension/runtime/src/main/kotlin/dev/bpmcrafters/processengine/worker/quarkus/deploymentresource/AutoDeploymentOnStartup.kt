package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource

import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.event.Observes
import jakarta.inject.Singleton

@Singleton
class AutoDeploymentOnStartup {
  fun start(@Observes event: StartupEvent, config: DeploymentConfig, deployment: ProcessDeployment) {
    if (config.enabled()) deployment.deployResources()
  }
}
