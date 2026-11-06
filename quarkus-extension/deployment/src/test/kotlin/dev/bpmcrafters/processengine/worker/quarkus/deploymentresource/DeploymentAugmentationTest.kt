package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource

import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.deploy.DeployBundleCommand
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.deploy.DeploymentInformation
import io.quarkus.test.QuarkusExtensionTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.assertj.core.api.Assertions.assertThat
import org.jboss.shrinkwrap.api.asset.StringAsset
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException
import java.time.Instant
import java.util.concurrent.CompletableFuture

class DeploymentAugmentationTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot {
      it.addClasses(Client::class.java, Client.Metadata::class.java)
        .addAsResource(StringAsset("process payload"), "deploy-smoke/order.bpmn")
        .addAsResource(StringAsset("decision payload"), "deploy-smoke/rules.dmn")
    }
      .withAdditionalDependency { it.addAsResource(StringAsset("unindexed dependency payload"), "deploy-smoke/order.bpmn") }
      .overrideConfigKey("dev.bpm-crafters.process-api.worker.enabled", "false")
      .overrideConfigKey("dev.bpm-crafters.process-api.worker.deployment.bpmn-resource-pattern", "classpath*:/deploy-smoke/**/*.bpmn")
      .overrideConfigKey("dev.bpm-crafters.process-api.worker.deployment.dmn-resource-pattern", "classpath*:/deploy-smoke/**/*.dmn")
  }

  @Inject
  lateinit var deployment: ProcessDeployment
  @Inject
  lateinit var resources: DeploymentResources
  @Inject
  lateinit var client: Client
  @Inject
  lateinit var config: DeploymentConfig
  @Test
  fun packagedResourcesAreAvailableForManualDeploymentWithStartupDisabled() {
    assertThat(config.enabled()).isFalse()
    assertThat(config.deploymentTimeoutInSeconds()).isEqualTo(30)
    assertThat(client.names).isEmpty()
    assertThat(resources.paths()).hasSize(3).allMatch { it.startsWith("META-INF/process-engine-worker/deployment/") }
    assertThat(deployment.deployResources()!!.deploymentKey).isEqualTo("smoke")
    assertThat(client.names).containsExactly("order.bpmn", "rules.dmn", "order.bpmn")
    assertThat(client.payloads).containsExactly("process payload", "decision payload", "unindexed dependency payload")
  }

  @Singleton
  class Client : DeploymentApi {
    val names = ArrayList<String>()
    val payloads = ArrayList<String>()
    override fun deploy(command: DeployBundleCommand): CompletableFuture<DeploymentInformation> {
      for (resource in command.resources) {
        names.add(resource.name)
        try {
          payloads.add(String(resource.resourceStream.readAllBytes(), Charsets.UTF_8))
        } catch (failure: IOException) {
          return CompletableFuture.failedFuture(failure)
        }
      }
      return CompletableFuture.completedFuture(DeploymentInformation("smoke", Instant.EPOCH, null))
    }

    override fun meta(caller: MetaInfoAware): MetaInfo = Metadata()
    class Metadata : MetaInfo
  }
}
