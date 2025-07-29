package dev.bpmcrafters.processengine.worker.process

import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.ApplicationListener

class AutoDeploymentOnStartup (
  private val processDeployment: ProcessDeployment
): ApplicationListener<ApplicationStartedEvent> {

  override fun onApplicationEvent(event: ApplicationStartedEvent) {
    processDeployment.deployResources()
  }

}
