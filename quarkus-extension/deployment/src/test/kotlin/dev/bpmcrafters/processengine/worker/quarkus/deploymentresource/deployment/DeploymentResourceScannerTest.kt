package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource.deployment

import io.quarkus.paths.PathTree
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class DeploymentResourceScannerTest {
  @TempDir
  lateinit var directory: Path
  @Test
  fun scansFilesAndUnindexedJarResourcesPreservingDuplicateNames() {
    Files.writeString(directory.resolve("root.bpmn"), "application")
    Files.createDirectories(directory.resolve("nested"))
    Files.writeString(directory.resolve("nested/rules.dmn"), "decision")
    val jar = jar()
    val resources = scan("classpath*:")
    assertThat(resources.map { it.originalPath }).containsExactly("nested/rules.dmn", "root.bpmn", "root.bpmn")
    assertThat(resources.map { it.generatedPath }).doesNotHaveDuplicates().allMatch { it.startsWith(DeploymentResourceScanner.PREFIX) }
    assertThat(resources[1].content).isEqualTo("application".toByteArray(Charsets.UTF_8))
    assertThat(resources[2].content).isEqualTo("dependency".toByteArray(Charsets.UTF_8))
    assertThat(scan("classpath*:")).usingRecursiveComparison().isEqualTo(resources)
    assertThat(Files.exists(jar)).isTrue()
  }

  @Test
  fun classpathWithoutStarKeepsFirstCopyAndRemovedResourcesAreNotIncluded() {
    Files.writeString(directory.resolve("root.bpmn"), "application")
    jar()
    assertThat(scan("classpath:")).hasSize(1)
    val source = DeploymentResourceScanner.Source(PathTree.ofDirectoryOrArchive(directory), setOf("root.bpmn"))
    assertThat(DeploymentResourceScanner().scan(listOf(source), patterns("classpath*:"))).isEmpty()
  }

  @Test
  fun emptyAndOverlappingPatternsDoNotInventOrDuplicateResources() {
    val source = DeploymentResourceScanner.Source(PathTree.ofDirectoryOrArchive(directory), emptySet())
    assertThat(DeploymentResourceScanner().scan(listOf(source), patterns("classpath*:"))).isEmpty()
    Files.writeString(directory.resolve("root.bpmn"), "payload")
    val pattern = ClasspathResourcePattern("classpath*:/**/*.bpmn")
    assertThat(DeploymentResourceScanner().scan(listOf(source), listOf(pattern, pattern))).hasSize(1)
  }

  private fun scan(prefix: String) = DeploymentResourceScanner().scan(
    listOf(
      DeploymentResourceScanner.Source(PathTree.ofDirectoryOrArchive(directory), emptySet()),
      DeploymentResourceScanner.Source(PathTree.ofArchive(directory.resolve("plain.jar")), emptySet())
    ), patterns(prefix)
  )

  @Test
  fun classpathWildcardExpandsOnlyFirstExistingDirectoryRoot() {
    val app = Files.createDirectories(directory.resolve("app/processes"))
    val dependency = Files.createDirectories(directory.resolve("dependency/processes"))
    Files.writeString(app.resolve("a.bpmn"), "app")
    Files.writeString(dependency.resolve("b.bpmn"), "dependency")
    assertThat(scanRoots("classpath:/processes/*.bpmn").map { it.originalPath }).containsExactly("processes/a.bpmn")
    assertThat(scanRoots("classpath*:/processes/*.bpmn")).hasSize(2)
  }

  @Test
  fun emptyFirstDirectoryDoesNotFallThroughButExactLookupDoes() {
    Files.createDirectories(directory.resolve("app/processes/nested"))
    val dependency = Files.createDirectories(directory.resolve("dependency/processes/nested"))
    Files.writeString(dependency.resolve("b.bpmn"), "dependency")
    assertThat(scanRoots("classpath:/processes/nested/*.bpmn")).isEmpty()
    assertThat(scanRoots("classpath:/processes/nested/b.bpmn")).hasSize(1)
    assertThat(scanRoots("classpath:/**/*.bpmn")).isEmpty()
  }

  @Test
  fun overlappingPatternsResolveTheirOwnRootsWithoutDuplicatingPhysicalResources() {
    val app = Files.createDirectories(directory.resolve("app/processes"))
    val dependency = Files.createDirectories(directory.resolve("dependency/processes/nested"))
    Files.writeString(app.resolve("a.bpmn"), "app")
    Files.writeString(dependency.resolve("b.bpmn"), "dependency")
    assertThat(
      scanRoots("classpath:/processes/**/*.bpmn", "classpath:/processes/nested/*.bpmn", "classpath*:/processes/**/*.bpmn")
        .map { it.originalPath }).containsExactly("processes/a.bpmn", "processes/nested/b.bpmn")
  }

  private fun scanRoots(vararg patterns: String): List<DeploymentResourceScanner.Resource> {
    val sources = listOf(
      DeploymentResourceScanner.Source(PathTree.ofDirectoryOrArchive(directory.resolve("app")), emptySet()),
      DeploymentResourceScanner.Source(PathTree.ofDirectoryOrArchive(directory.resolve("dependency")), emptySet())
    )
    return DeploymentResourceScanner().scan(sources, patterns.map(::ClasspathResourcePattern))
  }

  @Test
  fun directoryLookupFindsUnindexedJarWithoutExplicitDirectoryEntries() {
    val app = Files.createDirectory(directory.resolve("app"))
    val path = directory.resolve("nested.jar")
    JarOutputStream(Files.newOutputStream(path)).use { jar ->
      jar.putNextEntry(JarEntry("processes/nested/order.bpmn"))
      jar.write("jar payload".toByteArray(Charsets.UTF_8))
      jar.closeEntry()
    }
    val sources = listOf(
      DeploymentResourceScanner.Source(PathTree.ofDirectoryOrArchive(app), emptySet()),
      DeploymentResourceScanner.Source(PathTree.ofArchive(path), emptySet())
    )
    val patterns = listOf(ClasspathResourcePattern("classpath:/processes/nested/*.bpmn"))
    assertThat(DeploymentResourceScanner().scan(sources, patterns)).hasSize(1)
    Files.createDirectories(app.resolve("processes/nested"))
    assertThat(DeploymentResourceScanner().scan(sources, patterns)).isEmpty()
  }

  private fun patterns(prefix: String) = listOf(ClasspathResourcePattern("$prefix/**/*.bpmn"), ClasspathResourcePattern("$prefix/**/*.dmn"))
  private fun jar(): Path {
    val path = directory.resolve("plain.jar")
    JarOutputStream(Files.newOutputStream(path)).use { jar ->
      jar.putNextEntry(JarEntry("root.bpmn"))
      jar.write("dependency".toByteArray(Charsets.UTF_8))
      jar.closeEntry()
    }
    return path
  }
}
