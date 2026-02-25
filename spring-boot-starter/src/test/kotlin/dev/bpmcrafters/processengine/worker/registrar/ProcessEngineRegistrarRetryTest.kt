package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.FailJobException
import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.time.Duration
import java.util.UUID

internal class ProcessEngineRegistrarRetryTest {

  val testSubject = ProcessEngineStarterRegistrar(
    mock(),
    mock(),
    mock(),
    mock(),
    mock(),
    mock(),
    mock(),
    mock(),
    mock()
  )

  @Test
  fun `use retry values from exception`() {
    val task = TaskInformation(UUID.randomUUID().toString(), mapOf())
    val retry = testSubject.calculateRetry(task, FailJobException(message = "Reason", cause = null, retryCount = 17, retryBackoff = Duration.ofMillis(100)))
    assertThat(retry.retryCount).isEqualTo(17)
    assertThat(retry.retryBackoff).isEqualTo(Duration.ofMillis(100))
  }

  @Test
  fun `use decremented retry value from task`() {
    val task = TaskInformation(UUID.randomUUID().toString(), mapOf(
      TaskInformation.RETRIES to "19"
    ))
    val retry = testSubject.calculateRetry(task, IllegalArgumentException("Wrong"))
    assertThat(retry.retryCount).isEqualTo(18)
    assertThat(retry.retryBackoff).isNull()
  }

  @Test
  fun `no value if nothing is provided`() {
    val task = TaskInformation(UUID.randomUUID().toString(), mapOf())
    val retry = testSubject.calculateRetry(task, IllegalArgumentException("Wrong"))
    assertThat(retry.retryCount).isNull()
    assertThat(retry.retryBackoff).isNull()
  }
}
