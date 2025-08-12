package dev.bpmcrafters.processengine.worker.itest.camunda7.external

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import java.util.*
import java.util.concurrent.TimeUnit.SECONDS

@Import(WorkerWithTransactionalAnnotation::class)
class ExternalTaskCompletionInTransactionTest : AbstractTransactionalBehaviorTest() {

  @Test
  fun `happy path create two verified valid entity`() {
    val name = "Jan-${UUID.randomUUID()}"
    val pi = startProcess(name = name, verified = true)
    assertThat(processInstanceIsRunning(pi)).isTrue()

    // worker takes over and creates entity
    await().atMost(30, SECONDS).untilAsserted {
      assertThat(processInstanceIsRunning(pi)).isFalse
    }
    assertThat(entityExistsForName(name)).isTrue()

    val name2 = "Jan-${UUID.randomUUID()}"
    val pi2 = startProcess(name = name2, verified = true)
    assertThat(processInstanceIsRunning(pi2)).isTrue()

    // worker takes over and creates entity
    await().atMost(30, SECONDS).untilAsserted {
      assertThat(processInstanceIsRunning(pi2)).isFalse
    }
    assertThat(entityExistsForName(name2)).isTrue()
  }

  @Test
  fun `create verified valid entity and fails to create a duplicate`() {
    val name = "Jan-${UUID.randomUUID()}"
    val pi = startProcess(name = name, verified = true)
    assertThat(processInstanceIsRunning(pi)).isTrue()

    // worker takes over and creates entity
    await().atMost(30, SECONDS).untilAsserted {
      assertThat(processInstanceIsRunning(pi)).isFalse
    }
    assertThat(entityExistsForName(name)).isTrue()

    val pi2 = startProcess(name = name, verified = true)
    assertThat(processInstanceIsRunning(pi2)).isTrue()

    // worker takes over and creates entity
    await().atMost(30, SECONDS).untilAsserted {
      assertThat(processInstanceIsRunning(pi2)).isTrue()
    }
  }

  @Test
  fun `create verified two valid entity and fails to complete the second task`() {
    val name = "Jan-${UUID.randomUUID()}"
    val pi = startProcess(name = name, verified = true)
    assertThat(processInstanceIsRunning(pi)).isTrue()

    // worker takes over and creates entity
    await().atMost(30, SECONDS).untilAsserted {
      assertThat(processInstanceIsRunning(pi)).isFalse
    }
    assertThat(entityExistsForName(name)).isTrue()

    val name2 = "Jan-${UUID.randomUUID()}"
    val pi2 = startProcess(name = name2, verified = true, apiCallShouldFail = true)
    assertThat(processInstanceIsRunning(pi2)).isTrue()

    // worker takes over and creates entity, but fails in completion
    await().atMost(30, SECONDS).untilAsserted {
      assertThat(processInstanceIsRunning(pi2)).isFalse
    }

    assertThat(entityExistsForName(name2)).isFalse()
  }
}
