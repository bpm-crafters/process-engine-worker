package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource.deployment

import dev.bpmcrafters.processengine.worker.quarkus.WorkerConfig
import dev.bpmcrafters.processengine.worker.quarkus.deploymentresource.*
import io.quarkus.deployment.configuration.BuildTimeConfigurationReader
import io.smallrye.config.ConfigValidationException
import io.smallrye.config.SmallRyeConfigBuilder
import io.smallrye.config.common.MapBackedConfigSource
import java.util.Properties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class DeploymentConfigurationTest {
  companion object { private const val PREFIX = "dev.bpm-crafters.process-api.worker.deployment." }
  @Test fun augmentationAcceptsRuntimeDeploymentKeysBesideFixedPatterns() {
    val properties = Properties()
    properties.setProperty(PREFIX + "enabled", "true")
    properties.setProperty(PREFIX + "deployment-timeout-in-seconds", "7")
    properties.setProperty(PREFIX + "bpmn-resource-pattern", "classpath*:/configured/*.bpmn")
    val reader = BuildTimeConfigurationReader(listOf(WorkerConfig::class.java, BpmnResourcePatternConfig::class.java,
      DmnResourcePatternConfig::class.java, DeploymentConfig::class.java))
    val config = reader.initConfiguration(properties, Properties(), emptyMap())
    assertThat(config.getConfigMapping(BpmnResourcePatternConfig::class.java).pattern()).isEqualTo("classpath*:/configured/*.bpmn")
  }
  @Test fun runtimeStillRejectsUnknownDeploymentKeys() {
    assertThatThrownBy { SmallRyeConfigBuilder().withMapping(DeploymentConfig::class.java)
      .withSources(object : MapBackedConfigSource("test", mapOf(PREFIX + "enabld" to "true")) {}).build() }
      .isInstanceOf(ConfigValidationException::class.java).hasMessageContaining("enabld")
  }
  @Test fun runtimeCombinesExactFixedKeysWithRuntimeSettingsAndDefaults() {
    val config = SmallRyeConfigBuilder().withMapping(WorkerConfig::class.java)
      .withMapping(BpmnResourcePatternConfig::class.java).withMapping(DmnResourcePatternConfig::class.java)
      .withMapping(DeploymentConfig::class.java)
      .withSources(object : MapBackedConfigSource("test", mapOf(PREFIX + "enabled" to "true",
        PREFIX + "deployment-timeout-in-seconds" to "7", PREFIX + "bpmn-resource-pattern" to "classpath:/*.bpmn")) {}).build()
    assertThat(config.getConfigMapping(DeploymentConfig::class.java).enabled()).isTrue()
    assertThat(config.getConfigMapping(DeploymentConfig::class.java).deploymentTimeoutInSeconds()).isEqualTo(7)
    assertThat(config.getConfigMapping(BpmnResourcePatternConfig::class.java).pattern()).isEqualTo("classpath:/*.bpmn")
    assertThat(config.getConfigMapping(DmnResourcePatternConfig::class.java).pattern()).isEqualTo("classpath*:/**/*.dmn")
  }
}
