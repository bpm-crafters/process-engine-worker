package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource

import dev.bpmcrafters.processengineapi.deploy.*
import java.io.ByteArrayInputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class ResourceProcessDeployment(
  private val api: DeploymentApi,
  private val resources: DeploymentResources,
  private val timeoutSeconds: Long
) : ProcessDeployment {
  init {
    require(timeoutSeconds > 0) { "Deployment timeout must be positive" }
  }

  override fun deployResources(): DeploymentInformation? {
    if (resources.paths().isEmpty()) return null
    try {
      return await(api.deploy(DeployBundleCommand(bundle(), null)))
    } catch (failure: InterruptedException) {
      Thread.currentThread().interrupt()
      throw failure
    }
  }

  private fun bundle(): List<NamedResource> = java.util.List.copyOf(resources.paths().map { path ->
    val content = resources.open(path).use { it.readAllBytes() }
    // Cancellation cannot stop every async client; closing this independent stream is harmless to late readers.
    NamedResource(path.substringAfterLast('/'), ByteArrayInputStream(content), emptyMap())
  })

  private fun await(future: CompletableFuture<DeploymentInformation>): DeploymentInformation? {
    try {
      return future.get(timeoutSeconds, TimeUnit.SECONDS)
    } catch (failure: InterruptedException) {
      future.cancel(true)
      throw failure
    } catch (failure: TimeoutException) {
      future.cancel(true)
      throw failure
    }
  }
}
