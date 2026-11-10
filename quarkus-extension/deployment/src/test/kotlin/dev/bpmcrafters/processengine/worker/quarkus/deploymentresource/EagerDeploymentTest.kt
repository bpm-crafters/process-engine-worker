package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource

import io.quarkus.runtime.Startup
import io.quarkus.test.QuarkusExtensionTest
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.assertj.core.api.Assertions.assertThat
import org.jboss.shrinkwrap.api.asset.StringAsset
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class EagerDeploymentTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot {
      it.addClasses(Eager::class.java, DeploymentAugmentationTest.Client::class.java, DeploymentAugmentationTest.Client.Metadata::class.java)
        .addAsResource(StringAsset("eager payload"), "eager-deployment/order.bpmn")
    }
      .overrideConfigKey("dev.bpm-crafters.process-api.worker.deployment.bpmn-resource-pattern", "classpath*:/eager-deployment/*.bpmn")
      .overrideConfigKey("dev.bpm-crafters.process-api.worker.deployment.dmn-resource-pattern", "classpath*:/eager-deployment/*.dmn")
  }

  @Inject
  lateinit var client: DeploymentAugmentationTest.Client
  @Test
  fun postConstructManualDeploymentSeesCompleteImmutableCatalog() {
    assertThat(client.names).containsExactly("order.bpmn")
    assertThat(client.payloads).containsExactly("eager payload")
  }

  @Startup
  @Singleton
  class Eager {
    @Inject
    lateinit var deployment: ProcessDeployment
    @PostConstruct
    fun initialize() {
      deployment.deployResources()
    }
  }
}
