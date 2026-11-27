package dev.bpmcrafters.processengine.worker.process

import dev.bpmcrafters.processengineapi.lifecycle.ProcessApplicationLifecycleContext
import dev.bpmcrafters.processengineapi.lifecycle.ProcessApplicationPhase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class AutoDeploymentOnStartupTest {

  private val processDeployment: ProcessDeployment = mock()
  private val autoDeploymentOnStartup = AutoDeploymentOnStartup(processDeployment)

  @Test
  fun `should deploy resources after subscriptions are registered`() {
    assertThat(autoDeploymentOnStartup.getPhase()).isEqualTo(ProcessApplicationPhase.SUBSCRIPTIONS_REGISTERED)

    autoDeploymentOnStartup.onPhase(ProcessApplicationLifecycleContext(ProcessApplicationPhase.SUBSCRIPTIONS_REGISTERED))

    verify(processDeployment).deployResources()
  }

}
