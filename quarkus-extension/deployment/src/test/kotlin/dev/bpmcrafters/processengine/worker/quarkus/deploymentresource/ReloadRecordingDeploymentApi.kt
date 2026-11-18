package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource

import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.deploy.DeployBundleCommand
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.deploy.DeploymentInformation
import jakarta.inject.Singleton
import java.io.IOException
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture

/** Records actual startup deployments across the isolated classloaders used by dev-mode reloads. */
@Singleton
class ReloadRecordingDeploymentApi : DeploymentApi {
  companion object {
    const val SNAPSHOT_PROPERTY = "process-engine-worker.test.resource-reload.snapshot"
    const val REVISION_PROPERTY = "process-engine-worker.test.resource-reload.revision"
  }

  override fun deploy(command: DeployBundleCommand): CompletableFuture<DeploymentInformation> {
    val bundle = TreeMap<String, String>()
    try {
      for (resource in command.resources) bundle[resource.name] = String(resource.resourceStream.readAllBytes(), Charsets.UTF_8)
    } catch (failure: IOException) {
      return CompletableFuture.failedFuture(failure)
    }
    synchronized(System.getProperties()) {
      System.setProperty(SNAPSHOT_PROPERTY, bundle.toString())
      val revision = System.getProperty(REVISION_PROPERTY, "0").toInt()
      System.setProperty(REVISION_PROPERTY, (revision + 1).toString())
    }
    return CompletableFuture.completedFuture(DeploymentInformation("reload", Instant.EPOCH, null))
  }

  override fun meta(caller: MetaInfoAware): MetaInfo = Metadata()
  class Metadata : MetaInfo
}
