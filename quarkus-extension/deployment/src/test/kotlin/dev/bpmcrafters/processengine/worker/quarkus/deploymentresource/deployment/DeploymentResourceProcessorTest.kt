package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource.deployment

import dev.bpmcrafters.processengine.worker.quarkus.deploymentresource.BpmnResourcePatternConfig
import dev.bpmcrafters.processengine.worker.quarkus.deploymentresource.DmnResourcePatternConfig
import io.quarkus.bootstrap.model.ApplicationModelBuilder
import io.quarkus.deployment.builditem.*
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem
import io.quarkus.deployment.configuration.ClassLoadingConfig
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem
import io.quarkus.maven.dependency.DependencyFlags
import io.quarkus.maven.dependency.ResolvedDependencyBuilder
import io.quarkus.paths.PathList
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class DeploymentResourceProcessorTest {
  @TempDir
  lateinit var directory: Path
  @Test
  fun substitutedApplicationRootAndUnindexedAdditionalJarReplaceOriginalTestArtifactContents() {
    val original = dependency("original-maven-artifact", 0).setScope("test")
    val model = ApplicationModelBuilder().setAppArtifact(original).build()
    val actual = Files.createDirectory(directory.resolve("actual-app"))
    Files.writeString(actual.resolve("same.bpmn"), "actual")
    val jarPath = directory.resolve("unindexed.jar")
    JarOutputStream(Files.newOutputStream(jarPath)).use { jar ->
      jar.putNextEntry(JarEntry("same.bpmn"))
      jar.write("additional".toByteArray(Charsets.UTF_8))
      jar.closeEntry()
    }
    val generated = ArrayList<GeneratedResourceBuildItem>()
    DeploymentResourceProcessor().resources(
      CurateOutcomeBuildItem(model), ArchiveRootBuildItem(actual),
      listOf(AdditionalApplicationArchiveBuildItem(PathList.of(jarPath))), bpmn(), dmn(), loading(), emptyList(),
      { generated.add(it) }, {}, {})
    assertThat(generated.map { String(it.data, Charsets.UTF_8) }).containsExactly("actual", "additional")
    assertThat(generated.map { it.name }).doesNotHaveDuplicates()
  }

  @Test
  fun scansOnlyApplicationAndEffectiveRuntimeClasspathAndRegistersNativeCopiesAndWatchPredicate() {
    val app = dependency("app", 0)
    val runtime = dependency("runtime", DependencyFlags.RUNTIME_CP)
    val buildOnly = dependency("build-only", DependencyFlags.DEPLOYMENT_CP)
    val excluded = dependency("excluded", DependencyFlags.RUNTIME_CP)
    Files.writeString(runtime.resolvedPaths.singlePath.resolve("removed.bpmn"), "removed")
    Files.writeString(runtime.resolvedPaths.singlePath.resolve("builditem.bpmn"), "removed")
    Files.writeString(runtime.resolvedPaths.singlePath.resolve("config.bpmn"), "removed")
    val model = ApplicationModelBuilder().setAppArtifact(app).addDependency(runtime).addDependency(buildOnly).addDependency(excluded)
      .addDependency(dependency("missing", DependencyFlags.RUNTIME_CP or DependencyFlags.MISSING_FROM_APPLICATION))
      .addDependency(dependency("tests", DependencyFlags.RUNTIME_CP).setScope("test"))
      .addRemovedResources(runtime.key, setOf("removed.bpmn")).build()
    val generated = ArrayList<GeneratedResourceBuildItem>()
    val nativeResources = ArrayList<NativeImageResourceBuildItem>()
    val watched = ArrayList<HotDeploymentWatchedFileBuildItem>()
    val paths = DeploymentResourceProcessor().resources(
      CurateOutcomeBuildItem(model), ArchiveRootBuildItem(app.resolvedPaths.singlePath),
      listOf(
        AdditionalApplicationArchiveBuildItem(
          PathList.of(
            buildOnly.resolvedPaths.singlePath,
            excluded.resolvedPaths.singlePath,
            runtime.resolvedPaths.singlePath
          )
        )
      ),
      bpmn(), dmn(), loading(), listOf(RemovedResourceBuildItem(runtime.key, setOf("builditem.bpmn"))),
      { generated.add(it) }, { nativeResources.add(it) }, { watched.add(it) })
    assertThat(generated.map { String(it.data, Charsets.UTF_8) }).containsExactly("app", "runtime")
    assertThat(nativeResources.flatMap { it.resources }).containsExactlyElementsOf(generated.map { it.name })
    assertThat(paths.paths()).containsExactlyElementsOf(generated.map { it.name })
    assertThat(watched).hasSize(1)
    assertThat(watched[0].isRestartNeeded).isTrue()
    assertThat(watched[0].locationPredicate.test("new.bpmn")).isTrue()
    assertThat(watched[0].locationPredicate.test("nested/new.dmn")).isTrue()
    assertThat(watched[0].locationPredicate.test("unrelated.txt")).isFalse()
  }

  private fun bpmn() = object : BpmnResourcePatternConfig {
    override fun pattern() = "classpath*:/**/*.bpmn"
  }

  private fun dmn() = object : DmnResourcePatternConfig {
    override fun pattern() = "classpath*:/**/*.dmn"
  }

  private fun dependency(artifact: String, flags: Int): ResolvedDependencyBuilder {
    val root = Files.createDirectory(directory.resolve(artifact))
    Files.writeString(root.resolve("$artifact.bpmn"), artifact)
    return ResolvedDependencyBuilder().setGroupId("test").setArtifactId(artifact).setVersion("1").setType("jar").setFlags(flags).setResolvedPath(root)
  }

  private fun loading() = object : ClassLoadingConfig {
    override fun parentFirstArtifacts(): Optional<List<String>> = Optional.empty()
    override fun reloadableArtifacts(): Optional<String> = Optional.empty()
    override fun removedArtifacts(): Optional<List<String>> = Optional.of(listOf("test:excluded"))
    override fun removedResources(): Map<String, Set<String>> = mapOf("test:runtime" to setOf("config.bpmn"))
  }
}
