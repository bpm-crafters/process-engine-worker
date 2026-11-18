package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource

import java.io.IOException
import java.io.InputStream

open class DeploymentResources(paths: List<String>) {
  private val paths = java.util.List.copyOf(paths)

  open fun paths(): List<String> = paths

  @Throws(IOException::class)
  open fun open(path: String): InputStream =
    Thread.currentThread().contextClassLoader.getResourceAsStream(path)
      ?: throw IOException("Packaged deployment resource is missing: $path")
}
