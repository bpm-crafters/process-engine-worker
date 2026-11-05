package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource

import io.quarkus.test.QuarkusExtensionTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.jboss.shrinkwrap.api.asset.StringAsset
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class AutomaticDeploymentTest {
  companion object {
    @JvmField @RegisterExtension
    val APP = QuarkusExtensionTest()
      .withApplicationRoot { it.addClasses(DeploymentAugmentationTest.Client::class.java, DeploymentAugmentationTest.Client.Metadata::class.java)
        .addAsResource(StringAsset("automatic payload"), "auto-deployment/order.bpmn") }
      .overrideConfigKey("dev.bpm-crafters.process-api.worker.enabled", "false")
      .overrideConfigKey("dev.bpm-crafters.process-api.worker.deployment.enabled", "true")
      .overrideConfigKey("dev.bpm-crafters.process-api.worker.deployment.bpmn-resource-pattern", "classpath*:/auto-deployment/**/*.bpmn")
      .overrideConfigKey("dev.bpm-crafters.process-api.worker.deployment.dmn-resource-pattern", "classpath*:/auto-deployment/**/*.dmn")
  }
  @Inject lateinit var client: DeploymentAugmentationTest.Client
  @Test fun defaultServiceDeploysOnceAtRuntimeDespiteDisabledWorkers() {
    assertThat(client.names).containsExactly("order.bpmn")
    assertThat(client.payloads).containsExactly("automatic payload")
  }
}
