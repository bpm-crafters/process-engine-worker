package dev.bpmcrafters.processengine.worker.itest.camunda7.external

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import dev.bpmcrafters.processengine.worker.itest.camunda7.external.application.AbstractExampleProcessWorker
import dev.bpmcrafters.processengine.worker.itest.camunda7.external.application.MyEntityService
import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.camunda.bpm.engine.RuntimeService
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import java.util.*
import java.util.concurrent.TimeUnit.SECONDS

@Import(NoTransactionalAnnotationSuccessTest.WorkerWithoutTransactionalAnnotation::class)
class NoTransactionalAnnotationSuccessTest : AbstractTransactionalBehaviorTest() {

  class WorkerWithoutTransactionalAnnotation(
    myEntityService: MyEntityService,
    runtimeService: RuntimeService,
  ) : AbstractExampleProcessWorker(myEntityService = myEntityService, runtimeService = runtimeService) {

    @ProcessEngineWorker(
      topic = "example.create-entity"
    )
    override fun createEntity(
      task: TaskInformation,
      @Variable(name = "name") name: String,
      @Variable(name = "verified") verified: Boolean,
      @Variable(name = "apiCallShouldFail") apiCallShouldFail: Boolean
    ): Map<String, Any> {
      return super.createEntity(task, name, verified, apiCallShouldFail)
    }

    @ProcessEngineWorker(
      topic = "example.verify-entity",
    )
    override fun verifyEntity(@Variable(name = "id") id: String) {
      super.verifyEntity(id)
    }
  }

  @Test
  fun `happy path create verified valid entity`() {
    val name = "Jan-${UUID.randomUUID()}"
    val pi = startProcess(name = name, verified = true)
    assertThat(processInstanceIsRunning(pi.id)).isTrue()

    // worker takes over and creates entity
    await().atMost(30, SECONDS).untilAsserted {
      assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pi.id).active().count()).isEqualTo(0)
    }
    assertThat(entityExistsForName(name)).isTrue()
  }

  @Test
  fun `happy path create verified valid entity -1`() {
    val name = "Jan-${UUID.randomUUID()}"
    val pi = startProcess(name = name, verified = true)
    assertThat(processInstanceIsRunning(pi.id)).isTrue()

    // worker takes over and creates entity
    await().atMost(30, SECONDS).untilAsserted {
      assertThat(processInstanceIsRunning(pi.id)).isFalse
    }
    assertThat(entityExistsForName(name)).isTrue()

    val pi2 = startProcess(name = name, verified = true)
    assertThat(processInstanceIsRunning(pi2.id)).isTrue()

    // worker takes over and creates entity
    await().atMost(30, SECONDS).untilAsserted {
      assertThat(processInstanceIsRunning(pi2.id)).isTrue()
    }
  }
}
