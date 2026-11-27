package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource.deployment

import io.quarkus.builder.item.SimpleBuildItem

class DeploymentResourcePathsBuildItem(paths: List<String>) : SimpleBuildItem() {
  private val paths = java.util.List.copyOf(paths)
  fun paths(): List<String> = paths
}
