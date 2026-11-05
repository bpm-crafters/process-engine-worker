package dev.bpmcrafters.processengine.worker.quarkus

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ConfigDocumentationMetadataTest {
  @Test
  fun retainsDescriptionsForEveryConfigurationInterface() {
    // Read this module's output, not another extension's identically named classpath resource.
    val output = Path.of(WorkerConfig::class.java.protectionDomain.codeSource.location.toURI())
    val resource = output.resolve("META-INF/quarkus-config-doc/quarkus-config-javadoc.json")
    assertThat(resource).describedAs("Generated Quarkus configuration descriptions").exists()
    val elements = Files.newInputStream(resource).use { ObjectMapper().readTree(it).path("elements") }
    val worker = "dev.bpmcrafters.processengine.worker.quarkus.WorkerConfig"
    val deployment = "dev.bpmcrafters.processengine.worker.quarkus.deploymentresource"
    val expected = mapOf(
      "$worker.enabled" to "Enable the worker runtime. Disabled applications do not resolve engine API beans.",
      "$worker.registerProcessWorkers" to "Register discovered CDI worker methods as task subscriptions.",
      "$worker.completeTasksBeforeCommit" to "Complete tasks before committing owned worker transactions by default.",
      "$worker.removeTaskResultOnCompletion" to "Remove cached task results after successful automatic completion.",
      "$worker.tenantId" to "Default tenant restriction, unless a worker annotation specifies a tenant.",
      "$deployment.BpmnResourcePatternConfig.pattern" to "Classpath Ant pattern for BPMN resources. Only classpath: and classpath*: are supported.",
      "$deployment.DmnResourcePatternConfig.pattern" to "Classpath Ant pattern for DMN resources. Only classpath: and classpath*: are supported.",
      "$deployment.DeploymentConfig.enabled" to "Deploy at startup independently of worker registration. Missing DeploymentApi skips deployment.",
      "$deployment.DeploymentConfig.deploymentTimeoutInSeconds" to "Maximum time to wait for the deployment future, in seconds. Must be positive."
    )
    expected.forEach { (key, description) ->
      assertThat(elements.path(key).path("description").asText()).describedAs(key).isEqualTo(description)
    }
  }
}
