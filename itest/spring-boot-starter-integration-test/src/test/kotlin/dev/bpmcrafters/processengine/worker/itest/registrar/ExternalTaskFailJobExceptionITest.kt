package dev.bpmcrafters.processengine.worker.itest.registrar

import dev.bpmcrafters.processengine.worker.fixture.worker.WorkerWithFailJobException
import dev.bpmcrafters.processengine.worker.itest.FixtureITestBase
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.time.Duration.Companion.seconds

@Import(WorkerWithFailJobException::class)
@TestPropertySource(
  properties = [
    "dev.bpm-crafters.process-api.worker.complete-tasks-before-commit=true"
  ]
)
class ExternalTaskFailJobExceptionITest : FixtureITestBase() {

  @Test
  fun `fail job exception will fail job with specified retries`() {
    val name = "Big or Lil' Someone ${UUID.randomUUID()}"
    val pi = startProcess(name = name, verified = true, simulateRandomTechnicalError = true)
    assertThat(processInstanceIsRunning(pi)).isTrue()
    await().atMost(30, SECONDS).untilAsserted {
      val task = getExternalTasks(pi)[0]
      assertThat(task.errorMessage).isEqualTo("Simulating a technical error for task ${task.id}")
      assertThat(task.retries!!).isEqualTo(3)
    }
    assertThat(entityExistsForName(name)).isFalse
  }

}
