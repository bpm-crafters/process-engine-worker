package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource

import io.quarkus.runtime.annotations.ConfigPhase
import io.quarkus.runtime.annotations.ConfigRoot
import io.smallrye.config.ConfigMapping
import io.smallrye.config.WithDefault
import io.smallrye.config.WithParentName

@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
@ConfigMapping(prefix = "dev.bpm-crafters.process-api.worker.deployment.dmn-resource-pattern")
fun interface DmnResourcePatternConfig {
  /** Classpath Ant pattern for DMN resources. Only classpath: and classpath*: are supported. */
  @WithDefault("classpath*:/**/*.dmn") @WithParentName
  fun pattern(): String
}
