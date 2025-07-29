package dev.bpmcrafters.processengine.worker.process

import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.boot.context.event.ApplicationStartedEvent

class AutoDeploymentOnStartupTest {

  private val processDeployment: ProcessDeployment = mock()
  private val autoDeploymentOnStartup = AutoDeploymentOnStartup(processDeployment)

  @Test
  fun `should deploy resources on application startup`() {
    val event = ApplicationStartedEvent(mock(), arrayOf("arg1"), mock(), mock())

    autoDeploymentOnStartup.onApplicationEvent(event)

    verify(processDeployment).deployResources()
  }

}
