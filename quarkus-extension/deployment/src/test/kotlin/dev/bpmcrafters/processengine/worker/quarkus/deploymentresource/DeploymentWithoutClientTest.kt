package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource

import io.quarkus.test.QuarkusExtensionTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.jboss.shrinkwrap.api.asset.StringAsset
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class DeploymentWithoutClientTest {
  companion object {
    @JvmField @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot { it.addAsResource(StringAsset("payload"), "without-client/order.bpmn") }
      .overrideConfigKey("dev.bpm-crafters.process-api.worker.deployment.enabled", "true")
      .overrideConfigKey("dev.bpm-crafters.process-api.worker.deployment.bpmn-resource-pattern", "classpath*:/without-client/**/*.bpmn")
      .overrideConfigKey("dev.bpm-crafters.process-api.worker.deployment.dmn-resource-pattern", "classpath*:/without-client/**/*.dmn")
  }
  @Inject lateinit var deployment: ProcessDeployment
  @Inject lateinit var resources: DeploymentResources
  @Test fun explicitEnabledWithoutClientSkipsLikeSpringConditionalOnBean() {
    assertThat(resources.paths()).hasSize(1)
    assertThat(deployment.deployResources()).isNull()
  }
}
