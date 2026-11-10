package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource

import dev.bpmcrafters.processengineapi.deploy.DeploymentInformation
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

/** Injectable deployment service. An application bean replaces the default service. */
fun interface ProcessDeployment {
  /** Null when the bundle is empty or no DeploymentApi is available. */
  @Throws(IOException::class, InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun deployResources(): DeploymentInformation?
}
