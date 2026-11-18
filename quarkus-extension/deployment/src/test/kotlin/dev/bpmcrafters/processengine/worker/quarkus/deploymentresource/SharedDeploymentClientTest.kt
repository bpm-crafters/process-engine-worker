package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource

import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import io.quarkus.test.QuarkusExtensionTest
import jakarta.enterprise.inject.Disposes
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.assertj.core.api.Assertions.assertThat
import org.jboss.shrinkwrap.api.asset.StringAsset
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class SharedDeploymentClientTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot {
      it.addClasses(
        Clients::class.java, DependentDeploymentClientTest.Client::class.java,
        DependentDeploymentClientTest.Client.Metadata::class.java, DependentDeploymentClientTest.TimeoutFuture::class.java
      )
        .addAsResource(StringAsset("payload"), "shared-client/order.bpmn")
    }
      .overrideConfigKey("dev.bpm-crafters.process-api.worker.deployment.bpmn-resource-pattern", "classpath*:/shared-client/*.bpmn")
      .overrideConfigKey("dev.bpm-crafters.process-api.worker.deployment.dmn-resource-pattern", "classpath*:/shared-client/*.dmn")
  }

  @Inject
  lateinit var deployment: ProcessDeployment
  @Inject
  lateinit var clients: Clients
  @Test
  fun pseudoSingletonProducerIsReusedAndNotDisposedByManualDeployment() {
    deployment.deployResources()
    deployment.deployResources()
    assertThat(clients.created).isEqualTo(1)
    assertThat(clients.disposed).isZero()
  }

  @Singleton
  class Clients {
    var created = 0
    var disposed = 0
    @Produces
    @Singleton
    fun create(): DeploymentApi {
      created++; return DependentDeploymentClientTest.Client("success")
    }

    fun dispose(@Disposes client: DeploymentApi) {
      disposed++
    }
  }
}
