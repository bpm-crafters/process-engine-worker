package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource

import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.deploy.*
import io.quarkus.test.QuarkusExtensionTest
import jakarta.enterprise.context.Dependent
import jakarta.enterprise.inject.Disposes
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Instant
import java.util.concurrent.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jboss.shrinkwrap.api.asset.StringAsset
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class DependentDeploymentClientTest {
  companion object {
    @JvmField @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot { it.addClasses(Clients::class.java, Client::class.java, Client.Metadata::class.java, TimeoutFuture::class.java)
      .addAsResource(StringAsset("payload"), "dependent-client/order.bpmn") }
      .overrideConfigKey("dev.bpm-crafters.process-api.worker.deployment.bpmn-resource-pattern", "classpath*:/dependent-client/*.bpmn")
      .overrideConfigKey("dev.bpm-crafters.process-api.worker.deployment.dmn-resource-pattern", "classpath*:/dependent-client/*.dmn")
  }
  @Inject lateinit var deployment: ProcessDeployment
  @Inject lateinit var clients: Clients
  @Test fun dependentProducerDisposesEveryInvocationIncludingFailedFutureAndTimeout() {
    deployment.deployResources()
    deployment.deployResources()
    assertThat(clients.created).isEqualTo(2)
    assertThat(clients.disposed).isEqualTo(2)
    clients.mode = "failure"
    assertThatThrownBy { deployment.deployResources() }.isInstanceOf(ExecutionException::class.java)
    clients.mode = "timeout"
    assertThatThrownBy { deployment.deployResources() }.isInstanceOf(TimeoutException::class.java)
    assertThat(clients.created).isEqualTo(4)
    assertThat(clients.disposed).isEqualTo(4)
  }
  @Singleton
  class Clients {
    var created = 0
    var disposed = 0
    var mode = "success"
    @Produces @Dependent fun create(): DeploymentApi { created++; return Client(mode) }
    fun dispose(@Disposes client: DeploymentApi) { disposed++ }
  }
  class Client(private val mode: String) : DeploymentApi {
    override fun deploy(command: DeployBundleCommand): CompletableFuture<DeploymentInformation> = when (mode) {
      "failure" -> CompletableFuture.failedFuture(IllegalStateException("expected"))
      "timeout" -> TimeoutFuture()
      else -> CompletableFuture.completedFuture(DeploymentInformation("test", Instant.EPOCH, null))
    }
    override fun meta(caller: MetaInfoAware): MetaInfo = Metadata()
    class Metadata : MetaInfo
  }
  class TimeoutFuture : CompletableFuture<DeploymentInformation>() {
    @Throws(TimeoutException::class)
    override fun get(duration: Long, unit: TimeUnit): DeploymentInformation = throw TimeoutException("expected")
  }
}
