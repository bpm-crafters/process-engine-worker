package dev.bpmcrafters.processengine.worker.itest.idempotency

import dev.bpmcrafters.processengine.worker.fixture.InMemoryIdempotencyRegistryConfiguration
import dev.bpmcrafters.processengine.worker.fixture.MyEntity
import dev.bpmcrafters.processengine.worker.fixture.MyEntityRepository
import dev.bpmcrafters.processengine.worker.fixture.worker.WorkerWithTransactionalAnnotation
import dev.bpmcrafters.processengine.worker.fixture.worker.WorkerWithoutTransactionalAnnotation
import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.idempotency.TaskLogEntry
import dev.bpmcrafters.processengine.worker.idempotency.TaskLogEntryRepository
import dev.bpmcrafters.processengine.worker.itest.FixtureITestBase
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.util.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@TestPropertySource(
  properties = [
    "dev.bpm-crafters.process-api.adapter.c7remote.service-tasks.retries=0",
    "dev.bpm-crafters.process-api.worker.complete-tasks-before-commit=false",
    "dev.bpm-crafters.process-api.worker.remove-task-result-on-completion=true"
  ]
)
@MockitoSpyBean(types = [IdempotencyRegistry::class])
@MockitoSpyBean(name = "c7remote-service-task-completion-api", types = [ServiceTaskCompletionApi::class])
abstract class IdempotencyITest : FixtureITestBase() {

  @Autowired
  private lateinit var idempotencyRegistry: IdempotencyRegistry

  @Autowired
  private lateinit var serviceTaskCompletionApi: ServiceTaskCompletionApi

  @Value($$"${dev.bpm-crafters.process-api.worker.remove-task-result-on-completion}")
  private var removeTaskResult: Boolean = false

  @Test
  fun `fetching the same task does not execute business logic again`() {
    val name = "Big or Lil' Someone ${UUID.randomUUID()}"
    doThrow(IllegalStateException("Many things have gone wrong while completing a task"))
      .`when`(serviceTaskCompletionApi)
      .completeTask(any())
    val processInstanceId = startProcess(name, verified = true)
    assertThat(processInstanceIsRunning(processInstanceId)).isTrue()
    await atMost 30.seconds.toJavaDuration() untilAsserted {
      val incidents = getIncidents(processInstanceId)
      assertThat(incidents).hasSize(1)
    }
    doCallRealMethod().`when`(serviceTaskCompletionApi).completeTask(any())
    setExternalTaskRetries(getExternalTasks(processInstanceId).first().id!!, 1)
    await atMost 30.seconds.toJavaDuration() untilAsserted {
      assertThat(processInstanceIsRunning(processInstanceId)).isFalse
    }
    val inOrder = inOrder(idempotencyRegistry)
    inOrder.verify(idempotencyRegistry).getTaskResult(any())
    inOrder.verify(idempotencyRegistry).register(any(), any())
    inOrder.verify(idempotencyRegistry).getTaskResult(any())
    inOrder.verify(idempotencyRegistry, never()).register(any(), any())
    if (removeTaskResult) {
      inOrder.verify(idempotencyRegistry).removeTaskResult(any())
    } else {
      inOrder.verify(idempotencyRegistry, never()).removeTaskResult(any())
    }
  }


  @Nested
  @Import(
    InMemoryIdempotencyRegistryConfiguration::class,
    WorkerWithoutTransactionalAnnotation::class
  )
  class InMemoryIdempotencyWithoutTransactionITest : IdempotencyITest()

  @TestPropertySource(properties = ["dev.bpm-crafters.process-api.worker.complete-tasks-before-commit=false"])
  class InMemoryIdempotencyWithoutTransactionNotRemovingTaskResultITest : InMemoryIdempotencyWithoutTransactionITest()

  @Nested
  @Import(
    InMemoryIdempotencyRegistryConfiguration::class,
    WorkerWithTransactionalAnnotation::class
  )
  class InMemoryIdempotencyWithTransactionITest : IdempotencyITest()

  @TestPropertySource(properties = ["dev.bpm-crafters.process-api.worker.complete-tasks-before-commit=false"])
  class InMemoryIdempotencyWithTransactionNotRemovingTaskResultITest : InMemoryIdempotencyWithTransactionITest()

  @Nested
  @Import(WorkerWithoutTransactionalAnnotation::class)
  @EntityScan(basePackageClasses = [TaskLogEntry::class])
  @EnableJpaRepositories(basePackageClasses = [TaskLogEntryRepository::class])
  class JpaIdempotencyWithoutTransactionITest : IdempotencyITest()

  @TestPropertySource(properties = ["dev.bpm-crafters.process-api.worker.complete-tasks-before-commit=false"])
  class JpaIdempotencyWithoutTransactionNotRemovingTaskResultITest : JpaIdempotencyWithoutTransactionITest()

  @Nested
  @Import(WorkerWithTransactionalAnnotation::class)
  @EntityScan(basePackageClasses = [TaskLogEntry::class])
  @EnableJpaRepositories(basePackageClasses = [TaskLogEntryRepository::class])
  class JpaIdempotencyWithTransactionITest : IdempotencyITest()

  @TestPropertySource(properties = ["dev.bpm-crafters.process-api.worker.complete-tasks-before-commit=false"])
  class JpaIdempotencyWithTransactionNotRemovingTaskResulITest : JpaIdempotencyWithTransactionITest()

}
