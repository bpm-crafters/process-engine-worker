package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource

import io.quarkus.runtime.RuntimeValue
import io.quarkus.runtime.annotations.Recorder

@Recorder
class DeploymentResourceRecorder {
  fun resources(paths: List<String>): RuntimeValue<DeploymentResources> = RuntimeValue(DeploymentResources(paths))
}
