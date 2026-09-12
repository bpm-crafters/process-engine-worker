package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.FailJobException
import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerProperties
import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.io.IOException
import java.time.Duration
import java.util.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

internal class ProcessEngineRegistrarRetryTest {

  val testSubject = ProcessEngineStarterRegistrar(
    ProcessEngineWorkerProperties(
      backoffExceptions = mutableMapOf(
        IOException::class.java to 5.minutes.toJavaDuration()
      )
    ),
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

  @Test
  fun `use backoff exceptions configuration for exception at chain end`() {
    val cause = IOException(IllegalStateException(NullPointerException()))
    val task = TaskInformation(UUID.randomUUID().toString(), mapOf(TaskInformation.RETRIES to "3"))
    val retry = testSubject.calculateRetry(task, cause)
    assertThat(retry.retryCount).isEqualTo(3)
    assertThat(retry.retryBackoff).isEqualTo(5.minutes.toJavaDuration())
  }

  @Test
  fun `use backoff exceptions configuration for exception at chain start`() {
    val cause = RuntimeException(IllegalStateException(IOException()))
    val task = TaskInformation(UUID.randomUUID().toString(), mapOf(TaskInformation.RETRIES to "3"))
    val retry = testSubject.calculateRetry(task, cause)
    assertThat(retry.retryCount).isEqualTo(3)
    assertThat(retry.retryBackoff).isEqualTo(5.minutes.toJavaDuration())
  }

  @Test
  fun `no failure on cyclic chains`() {
    val cause = RuntimeException(IllegalStateException())
    cause.cause?.initCause(cause)
    val task = TaskInformation(UUID.randomUUID().toString(), mapOf(TaskInformation.RETRIES to "3"))
    val retry = testSubject.calculateRetry(task, cause)
    assertThat(retry.retryCount).isEqualTo(2)
    assertThat(retry.retryBackoff).isNull()
  }
}
