package dev.bpmcrafters.processengine.worker.process

import dev.bpmcrafters.processengineapi.lifecycle.ProcessApplicationLifecycleContext
import dev.bpmcrafters.processengineapi.lifecycle.ProcessApplicationLifecycleParticipant
import dev.bpmcrafters.processengineapi.lifecycle.ProcessApplicationPhase

/**
 * Trigger to deploy resources (for instance, processes) on start-up of the application,
 * hooked in the process-engine-api lifecycle.
 * @since 0.5.0
 **/
class AutoDeploymentOnStartup(
  private val processDeployment: ProcessDeployment
) : ProcessApplicationLifecycleParticipant {

  override fun getPhase(): Int = ProcessApplicationPhase.SUBSCRIPTIONS_REGISTERED

  /**
   * Deploys the configured process and decision resources after task subscription registration.
   **/
  override fun onPhase(context: ProcessApplicationLifecycleContext) {
    processDeployment.deployResources()
  }
}
