package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource

import io.quarkus.runtime.StartupEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class AutoDeploymentOnStartupTest {
  @Test
  fun defaultDisabledAndExplicitEnabledAreIndependentOfWorkerConfiguration() {
    val calls = AtomicInteger()
    val custom = ProcessDeployment { calls.incrementAndGet(); null }
    val startup = AutoDeploymentOnStartup()
    startup.start(StartupEvent(), config(false), custom)
    assertThat(calls).hasValue(0)
    startup.start(StartupEvent(), config(true), custom)
    assertThat(calls).hasValue(1)
  }

  private fun config(enabled: Boolean) = object : DeploymentConfig {
    override fun enabled() = enabled
    override fun deploymentTimeoutInSeconds() = 30L
  }
}
