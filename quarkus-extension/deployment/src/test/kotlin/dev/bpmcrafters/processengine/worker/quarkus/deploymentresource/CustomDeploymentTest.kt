package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource

import dev.bpmcrafters.processengineapi.deploy.DeploymentInformation
import io.quarkus.test.QuarkusExtensionTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.atomic.AtomicInteger

class CustomDeploymentTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot { it.addClass(Custom::class.java) }
      .overrideConfigKey("dev.bpm-crafters.process-api.worker.enabled", "false")
      .overrideConfigKey("dev.bpm-crafters.process-api.worker.deployment.enabled", "true")
  }

  @Inject
  lateinit var deployment: ProcessDeployment
  @Inject
  lateinit var custom: Custom
  @Test
  fun startupUsesApplicationOverrideEvenWithoutEngineApiOrEnabledWorkers() {
    assertThat(deployment).isSameAs(custom)
    assertThat(custom.calls).hasValue(1)
  }

  @Singleton
  class Custom : ProcessDeployment {
    val calls = AtomicInteger()
    override fun deployResources(): DeploymentInformation? {
      calls.incrementAndGet(); return null
    }
  }
}
