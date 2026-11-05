package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource.deployment

import dev.bpmcrafters.processengine.worker.quarkus.deploymentresource.*
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import io.quarkus.arc.deployment.AdditionalBeanBuildItem
import io.quarkus.arc.deployment.SyntheticBeanBuildItem
import io.quarkus.arc.deployment.UnremovableBeanBuildItem
import io.quarkus.deployment.annotations.BuildProducer
import io.quarkus.deployment.annotations.BuildStep
import io.quarkus.deployment.annotations.ExecutionTime
import io.quarkus.deployment.annotations.Record
import io.quarkus.deployment.builditem.*
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem
import io.quarkus.deployment.configuration.ClassLoadingConfig
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem
import io.quarkus.maven.dependency.ArtifactKey
import io.quarkus.maven.dependency.DependencyFlags
import io.quarkus.maven.dependency.ResolvedDependency
import io.quarkus.paths.PathTree
import jakarta.inject.Singleton
import java.nio.file.Path

class DeploymentResourceProcessor {
  @BuildStep
  fun beans(): AdditionalBeanBuildItem = AdditionalBeanBuildItem.builder().setUnremovable()
    .addBeanClasses(DeploymentBeans::class.java, AutoDeploymentOnStartup::class.java).build()

  @BuildStep
  fun clients(): UnremovableBeanBuildItem =
    UnremovableBeanBuildItem.beanTypes(DeploymentApi::class.java, ProcessDeployment::class.java)

  @BuildStep
  fun resources(
    curate: CurateOutcomeBuildItem, applicationRoot: ArchiveRootBuildItem,
    additionalArchives: List<AdditionalApplicationArchiveBuildItem>,
    bpmn: BpmnResourcePatternConfig, dmn: DmnResourcePatternConfig, loading: ClassLoadingConfig,
    removed: List<RemovedResourceBuildItem>, generated: BuildProducer<GeneratedResourceBuildItem>,
    nativeResources: BuildProducer<NativeImageResourceBuildItem>, watched: BuildProducer<HotDeploymentWatchedFileBuildItem>,
  ): DeploymentResourcePathsBuildItem {
    val patterns = listOf(ClasspathResourcePattern(bpmn.pattern()), ClasspathResourcePattern(dmn.pattern()))
    val sources = sources(curate, applicationRoot, additionalArchives, loading, removed)
    val resources = DeploymentResourceScanner().scan(sources, patterns)
    for (resource in resources) {
      generated.produce(GeneratedResourceBuildItem(resource.generatedPath, resource.content))
      nativeResources.produce(NativeImageResourceBuildItem(resource.generatedPath))
    }
    watched.produce(HotDeploymentWatchedFileBuildItem.builder().setRestartNeeded(true)
      .setLocationPredicate { name -> patterns.any { it.matches(name) } }.build())
    return DeploymentResourcePathsBuildItem(resources.map { it.generatedPath })
  }

  @BuildStep
  @Record(ExecutionTime.STATIC_INIT)
  fun catalog(paths: DeploymentResourcePathsBuildItem, recorder: DeploymentResourceRecorder): SyntheticBeanBuildItem =
    // Only immutable path metadata is initialized here; no runtime config, resource I/O, or API calls.
    SyntheticBeanBuildItem.configure(DeploymentResources::class.java).scope(Singleton::class.java).unremovable()
      .runtimeValue(recorder.resources(paths.paths())).done()

  private fun sources(
    curate: CurateOutcomeBuildItem, applicationRoot: ArchiveRootBuildItem,
    additionalArchives: List<AdditionalApplicationArchiveBuildItem>, loading: ClassLoadingConfig,
    removed: List<RemovedResourceBuildItem>,
  ): List<DeploymentResourceScanner.Source> {
    val model = curate.applicationModel
    val excluded = loading.removedArtifacts().orElse(emptyList()).mapTo(HashSet()) { ArtifactKey.fromString(it) }
    val sources = ArrayList<DeploymentResourceScanner.Source>()
    val seen = HashSet<Path>()
    // Tests and dev mode can substitute application roots without changing the resolved Maven artifact.
    val appRemoved = removedPaths(model.appArtifact.key, model.removedResources, loading, removed)
    for (root in applicationRoot.rootDirectories) {
      addSource(PathTree.ofDirectoryOrArchive(root), appRemoved, seen, sources)
    }
    applicationRoot.resolvedPaths.forEach { seen.add(it) }
    for (dependency in model.getDependenciesWithAnyFlag(DependencyFlags.RUNTIME_CP, DependencyFlags.DEPLOYMENT_CP)) {
      if (!dependency.isRuntimeCp || unavailable(dependency, excluded)) dependency.resolvedPaths.forEach { seen.add(it) }
    }
    for (dependency in model.runtimeDependencies) {
      if (unavailable(dependency, excluded)) continue
      addSource(dependency.contentTree, removedPaths(dependency.key, model.removedResources, loading, removed), seen, sources)
    }
    for (archive in additionalArchives) {
      for (root in archive.resolvedPaths) addSource(PathTree.ofDirectoryOrArchive(root), emptySet(), seen, sources)
    }
    return sources
  }

  private fun unavailable(dependency: ResolvedDependency, excluded: Set<ArtifactKey>) =
    dependency.key in excluded || dependency.isFlagSet(DependencyFlags.MISSING_FROM_APPLICATION) || dependency.scope == "test"

  private fun addSource(tree: PathTree, removed: Set<String>, seen: MutableSet<Path>, sources: MutableList<DeploymentResourceScanner.Source>) {
    val excluded = tree.roots.filter { it in seen }
    if (excluded.size == tree.roots.size) return
    // Walk the original tree to retain its filters and order, suppressing only previously visited roots.
    sources.add(DeploymentResourceScanner.Source.excludingRoots(tree, removed, excluded))
    seen.addAll(tree.roots)
  }

  private fun removedPaths(
    artifact: ArtifactKey, modelRemoved: Map<ArtifactKey, Set<String>>, loading: ClassLoadingConfig,
    removed: List<RemovedResourceBuildItem>,
  ): Set<String> {
    val paths = HashSet(modelRemoved.getOrDefault(artifact, emptySet()))
    loading.removedResources().forEach { (key, values) -> if (ArtifactKey.fromString(key) == artifact) paths.addAll(values) }
    removed.filter { it.artifact == artifact }.forEach { paths.addAll(it.resources) }
    return paths
  }
}
