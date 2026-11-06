package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource

import io.quarkus.runtime.annotations.ConfigPhase
import io.quarkus.runtime.annotations.ConfigRoot
import io.smallrye.config.ConfigMapping
import io.smallrye.config.WithDefault

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "dev.bpm-crafters.process-api.worker.deployment")
interface DeploymentConfig {
  /** Deploy at startup independently of worker registration. Missing DeploymentApi skips deployment. */
  @WithDefault("false")
  fun enabled(): Boolean

  /** Maximum time to wait for the deployment future, in seconds. Must be positive. */
  @WithDefault("30")
  fun deploymentTimeoutInSeconds(): Long
}
