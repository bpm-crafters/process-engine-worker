package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.itest.AbstractBehaviorIT
import dev.bpmcrafters.processengine.worker.itest.WorkerWithTransactionalAnnotation
import org.assertj.core.api.Assertions
import org.awaitility.Awaitility
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import java.util.UUID
import java.util.concurrent.TimeUnit

@Import(WorkerWithTransactionalAnnotation::class)
@TestPropertySource(properties = [
  "dev.bpm-crafters.process-api.worker.complete-tasks-before-commit=true"
])
class ExternalTaskCompletionBeforeCommitIT : AbstractBehaviorIT() {

  @Test
  fun `happy path create two verified valid entity`() {
    val name = "Jan-${UUID.randomUUID()}"
    val pi = startProcess(name = name, verified = true)
    Assertions.assertThat(processInstanceIsRunning(pi)).isTrue()

    // worker takes over and creates entity
    Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted {
      Assertions.assertThat(processInstanceIsRunning(pi)).isFalse
    }
    Assertions.assertThat(entityExistsForName(name)).isTrue()

    val name2 = "Jan-${UUID.randomUUID()}"
    val pi2 = startProcess(name = name2, verified = true)
    Assertions.assertThat(processInstanceIsRunning(pi2)).isTrue()

    // worker takes over and creates entity
    Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted {
      Assertions.assertThat(processInstanceIsRunning(pi2)).isFalse
    }
    Assertions.assertThat(entityExistsForName(name2)).isTrue()
  }

  @Test
  fun `create verified valid entity and fails to create a duplicate`() {
    val name = "Jan-${UUID.randomUUID()}"
    val pi = startProcess(name = name, verified = true)
    Assertions.assertThat(processInstanceIsRunning(pi)).isTrue()

    // worker takes over and creates entity
    Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted {
      Assertions.assertThat(processInstanceIsRunning(pi)).isFalse
    }
    Assertions.assertThat(entityExistsForName(name)).isTrue()

    val pi2 = startProcess(name = name, verified = true)
    Assertions.assertThat(processInstanceIsRunning(pi2)).isTrue()

    // worker takes over and creates entity
    Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted {
      Assertions.assertThat(processInstanceIsRunning(pi2)).isTrue()
    }
  }

  @Test
  fun `create verified two valid entity and fails to complete the second task`() {
    val name = "Jan-${UUID.randomUUID()}"
    val pi = startProcess(name = name, verified = true)
    Assertions.assertThat(processInstanceIsRunning(pi)).isTrue()

    // worker takes over and creates entity
    Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted {
      Assertions.assertThat(processInstanceIsRunning(pi)).isFalse
    }
    Assertions.assertThat(entityExistsForName(name)).isTrue()

    val name2 = "Jan-${UUID.randomUUID()}"
    val pi2 = startProcess(name = name2, verified = true, apiCallShouldFail = true)
    Assertions.assertThat(processInstanceIsRunning(pi2)).isTrue()

    // worker takes over and creates entity, but fails in completion
    Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted {
      Assertions.assertThat(processInstanceIsRunning(pi2)).isFalse
    }

    Assertions.assertThat(entityExistsForName(name2)).isFalse()
  }
}
