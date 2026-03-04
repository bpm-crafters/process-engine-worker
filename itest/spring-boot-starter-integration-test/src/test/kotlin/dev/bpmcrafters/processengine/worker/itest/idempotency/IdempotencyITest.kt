package dev.bpmcrafters.processengine.worker.itest.idempotency

import dev.bpmcrafters.processengine.worker.fixture.InMemoryIdempotencyRegistryConfiguration
import dev.bpmcrafters.processengine.worker.fixture.TestApplication
import dev.bpmcrafters.processengine.worker.fixture.worker.WorkerWithTransactionalAnnotation
import dev.bpmcrafters.processengine.worker.fixture.worker.WorkerWithoutTransactionalAnnotation
import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.itest.FixtureITestBase
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import org.assertj.core.api.Assertions
import org.awaitility.Awaitility
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.util.*
import java.util.concurrent.TimeUnit

@SpringBootTest(classes = [TestApplication::class])
@TestPropertySource(
  properties = [
    "dev.bpm-crafters.process-api.worker.complete-tasks-before-commit=false"
  ]
)
@MockitoSpyBean(types = [IdempotencyRegistry::class])
@MockitoSpyBean(name = "c7remote-service-task-completion-api", types = [ServiceTaskCompletionApi::class])
abstract class IdempotencyITest : FixtureITestBase() {

  @Autowired
  private lateinit var idempotencyRegistry: IdempotencyRegistry

  @Autowired
  private lateinit var serviceTaskCompletionApi: ServiceTaskCompletionApi

  @Test
  fun `fetching the same task does not execute business logic again`() {
    val name = "Big or Lil' Someone ${UUID.randomUUID()}"
    doThrow(IllegalStateException("Many things have gone wrong while completing a task"))
      .`when`(serviceTaskCompletionApi)
      .completeTask(any())
    val processInstanceId = startProcess(name, verified = true)
    Assertions.assertThat(processInstanceIsRunning(processInstanceId)).isTrue()
    Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted {
      val entity = findEntityByName(name)
      Assertions.assertThat(entity).isNotNull
    }
    doCallRealMethod().`when`(serviceTaskCompletionApi).completeTask(any())
    unlockExternalTask(getExternalTasks(processInstanceId).first().id!!)
    print(idempotencyRegistry)
    Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted {
      Assertions.assertThat(processInstanceIsRunning(processInstanceId)).isFalse
    }
    val inOrder = inOrder(idempotencyRegistry)
    inOrder.verify(idempotencyRegistry).getTaskResult(any())
    inOrder.verify(idempotencyRegistry).register(any(), any())
    inOrder.verify(idempotencyRegistry).getTaskResult(any())
    inOrder.verify(idempotencyRegistry, never()).register(any(), any())
  }


  @Nested
  @Import(
    InMemoryIdempotencyRegistryConfiguration::class,
    WorkerWithoutTransactionalAnnotation::class
  )
  class InMemoryIdempotencyWithoutTransactionITest : IdempotencyITest()

  @Nested
  @Import(
    InMemoryIdempotencyRegistryConfiguration::class,
    WorkerWithTransactionalAnnotation::class
  )
  class InMemoryIdempotencyWithTransactionITest : IdempotencyITest()

  @Import(WorkerWithTransactionalAnnotation::class)
  class JpaIdempotencyWithTransactionITest : IdempotencyITest()

}

