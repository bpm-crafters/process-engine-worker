package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource.deployment

import dev.bpmcrafters.processengine.worker.quarkus.deploymentresource.BpmnResourcePatternConfig
import dev.bpmcrafters.processengine.worker.quarkus.deploymentresource.DmnResourcePatternConfig
import io.quarkus.bootstrap.model.ApplicationModel
import io.quarkus.deployment.builditem.AdditionalApplicationArchiveBuildItem
import io.quarkus.deployment.builditem.ArchiveRootBuildItem
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem
import io.quarkus.deployment.configuration.ClassLoadingConfig
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem
import io.quarkus.maven.dependency.DependencyFlags
import io.quarkus.maven.dependency.ResolvedDependencyBuilder
import io.quarkus.paths.MultiRootPathTree
import io.quarkus.paths.PathFilter
import io.quarkus.paths.PathList
import io.quarkus.paths.PathTree
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class PartiallyOverlappingResourceRootsTest {
  @TempDir
  lateinit var directory: Path
  @Test
  fun eachPhysicalRootIsScannedOnceWithoutLosingFiltersOrDistinctJarCopies() {
    assertThat(scan("classpath*:/**/*.bpmn")).containsExactly("application", "first", "second", "jar-one", "jar-two")
  }

  @Test
  fun firstRootClasspathLookupStillHonorsRemainingRootOrder() {
    assertThat(scan("classpath:/nested/*.bpmn")).containsExactly("first")
  }

  @Test
  fun excludedArchiveRootsMatchOpenedZipVisitsWithoutSuppressingAnotherJarWithTheSameLogicalPath() {
    val first = jar("jar-one").resolvedPaths.singlePath
    val second = jar("jar-two").resolvedPaths.singlePath
    val combined = MultiRootPathTree(PathTree.ofArchive(first), PathTree.ofArchive(second)).filter(PathFilter.forIncludes(listOf("same.bpmn")))
    val sources = listOf(
      DeploymentResourceScanner.Source(PathTree.ofArchive(first), emptySet()),
      DeploymentResourceScanner.Source.excludingRoots(combined, emptySet(), listOf(first))
    )
    val resources = DeploymentResourceScanner().scan(sources, listOf(ClasspathResourcePattern("classpath*:/**/*.bpmn")))
    assertThat(resources.map { String(it.content, Charsets.UTF_8) }).containsExactly("jar-one", "jar-two")
  }

  private fun scan(pattern: String): List<String> {
    val application = Files.createDirectory(directory.resolve("application"))
    Files.writeString(application.resolve("app.bpmn"), "application")
    val first = resourceRoot("first")
    val second = resourceRoot("second")
    val tree = MultiRootPathTree(PathTree.ofDirectoryOrArchive(application), PathTree.ofDirectoryOrArchive(first), PathTree.ofDirectoryOrArchive(second))
      .filter(PathFilter.forExcludes(listOf("nested/filtered.bpmn")))
    val dependency = dependency(tree, application, first, second)
    val model = mock(ApplicationModel::class.java)
    `when`(model.appArtifact).thenReturn(dependency)
    `when`(model.runtimeDependencies).thenReturn(listOf(dependency))
    `when`(model.getDependenciesWithAnyFlag(DependencyFlags.RUNTIME_CP, DependencyFlags.DEPLOYMENT_CP)).thenReturn(listOf(dependency))
    `when`(model.removedResources).thenReturn(mapOf(dependency.key to setOf("nested/removed.bpmn")))
    return generate(model, application, pattern)
  }

  private fun generate(model: ApplicationModel, application: Path, pattern: String): List<String> {
    val generated = ArrayList<GeneratedResourceBuildItem>()
    DeploymentResourceProcessor().resources(
      CurateOutcomeBuildItem(model), ArchiveRootBuildItem(application), listOf(jar("jar-one"), jar("jar-two")),
      object : BpmnResourcePatternConfig {
        override fun pattern() = pattern
      },
      object : DmnResourcePatternConfig {
        override fun pattern() = "classpath*:/no-match/*.dmn"
      }, loading(), emptyList(),
      { generated.add(it) }, {}, {})
    return generated.map { String(it.data, Charsets.UTF_8) }
  }

  private fun dependency(tree: PathTree, vararg roots: Path): ResolvedDependencyBuilder {
    val dependency = object : ResolvedDependencyBuilder() {
      override fun getContentTree(): PathTree = tree
    }
    return dependency.setGroupId("test").setArtifactId("workspace").setVersion("1").setType("jar")
      .setFlags(DependencyFlags.RUNTIME_CP).setResolvedPaths(PathList.of(*roots))
  }

  private fun resourceRoot(name: String): Path {
    val root = Files.createDirectories(directory.resolve(name).resolve("nested"))
    Files.writeString(root.resolve("process.bpmn"), name)
    Files.writeString(root.resolve("filtered.bpmn"), "must stay filtered")
    Files.writeString(root.resolve("removed.bpmn"), "must stay removed")
    return root.parent
  }

  private fun jar(content: String): AdditionalApplicationArchiveBuildItem {
    val path = directory.resolve("$content.jar")
    JarOutputStream(Files.newOutputStream(path)).use { jar ->
      jar.putNextEntry(JarEntry("same.bpmn"))
      jar.write(content.toByteArray(Charsets.UTF_8))
      jar.closeEntry()
    }
    return AdditionalApplicationArchiveBuildItem(PathList.of(path))
  }

  private fun loading() = object : ClassLoadingConfig {
    override fun parentFirstArtifacts(): Optional<List<String>> = Optional.empty()
    override fun reloadableArtifacts(): Optional<String> = Optional.empty()
    override fun removedArtifacts(): Optional<List<String>> = Optional.empty()
    override fun removedResources(): Map<String, Set<String>> = emptyMap()
  }
}
