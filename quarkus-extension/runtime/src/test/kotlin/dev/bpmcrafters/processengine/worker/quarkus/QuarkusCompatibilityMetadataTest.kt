package dev.bpmcrafters.processengine.worker.quarkus

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

class QuarkusCompatibilityMetadataTest {
  // Use this runtime artifact's output, not a dependency's identically named descriptor.
  private val metadata = Path.of(WorkerConfig::class.java.protectionDomain.codeSource.location.toURI())
    .resolve("META-INF")

  @Test
  fun propertiesDeclareExactMinimumQuarkusVersion() {
    val properties = Properties().apply {
      Files.newInputStream(metadata.resolve("quarkus-extension.properties")).use { load(it) }
    }
    assertThat(properties.getProperty("deployment-artifact"))
      .matches("dev\\.bpm-crafters\\.process-engine-worker:process-engine-worker-quarkus-deployment:[^:]+")
    assertThat(properties.getProperty("requires-quarkus-version")).isEqualTo("[3.37.3,)")
  }

  @Test
  fun yamlDeclaresExactMinimumQuarkusVersion() {
    assertThat(yamlScalar("requires-quarkus-core")).isEqualTo("[3.37.3,)")
  }

  @Test
  fun yamlRecordsTheResolvedQuarkusCoreVersion() {
    val core = Properties().apply {
      val resource = "META-INF/maven/io.quarkus/quarkus-core/pom.properties"
      QuarkusCompatibilityMetadataTest::class.java.classLoader.getResourceAsStream(resource)
        .use { load(requireNotNull(it) { resource }) }
    }
    assertThat(core.getProperty("version")).isNotBlank()
    assertThat(yamlScalar("built-with-quarkus-core")).isEqualTo(core.getProperty("version"))
  }

  private fun yamlScalar(key: String): String {
    val values = Files.readAllLines(metadata.resolve("quarkus-extension.yaml"))
      .filter { it.substringBefore(':').trim() == key }
      .map { it.substringAfter(':').trim().removeSurrounding("\"").removeSurrounding("'") }
    assertThat(values).describedAs("Generated YAML scalar %s", key).hasSize(1)
    return values.single()
  }
}
