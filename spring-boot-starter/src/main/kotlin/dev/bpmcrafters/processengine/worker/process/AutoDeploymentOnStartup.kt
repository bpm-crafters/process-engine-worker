package dev.bpmcrafters.processengine.worker.process

import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.ApplicationListener

/**
 * Trigger to deploy resources (for instance, processes) on start-up of the application.
 * @since 0.5.0
 */
class AutoDeploymentOnStartup (
  private val processDeployment: ProcessDeployment
): ApplicationListener<ApplicationStartedEvent> {

  override fun onApplicationEvent(event: ApplicationStartedEvent) {
    processDeployment.deployResources()
  }
}
