package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource

import io.quarkus.runtime.annotations.ConfigPhase
import io.quarkus.runtime.annotations.ConfigRoot
import io.smallrye.config.ConfigMapping
import io.smallrye.config.WithDefault
import io.smallrye.config.WithParentName

@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
@ConfigMapping(prefix = "dev.bpm-crafters.process-api.worker.deployment.bpmn-resource-pattern")
fun interface BpmnResourcePatternConfig {
  /** Classpath Ant pattern for BPMN resources. Only classpath: and classpath*: are supported. */
  @WithDefault("classpath*:/**/*.bpmn") @WithParentName
  fun pattern(): String
}
