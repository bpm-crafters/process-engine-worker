package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred
import dev.bpmcrafters.processengine.worker.FailJobException
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerConfiguration
import dev.bpmcrafters.processengine.worker.idempotency.InMemoryIdempotencyRegistry
import dev.bpmcrafters.processengine.worker.transaction.TransactionalExecutor
import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.task.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.lang.reflect.InvocationTargetException
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * Tests the framework-independent registrar: subscription, invocation, completion, error reporting and transactions.
 */
internal class ProcessEngineWorkerRegistrarTest {

  private val taskSubscriptionApi = mock<TaskSubscriptionApi> {
    on { subscribeForTask(any()) } doReturn CompletableFuture.completedFuture(mock())
  }
  private val taskCompletionApi = mock<ServiceTaskCompletionApi> {
    on { completeTask(any()) } doReturn CompletableFuture.completedFuture(Empty)
    on { completeTaskByError(any()) } doReturn CompletableFuture.completedFuture(Empty)
    on { failTask(any()) } doReturn CompletableFuture.completedFuture(Empty)
  }
  private val variableConverter = JacksonVariableConverter(com.fasterxml.jackson.databind.ObjectMapper())
  private val metrics = mock<ProcessEngineWorkerMetrics>()

  /**
   * Records transaction boundaries and after-commit hooks.
   */
  private class RecordingTransactionalExecutor : TransactionalExecutor {
    val events = mutableListOf<String>()
    private var active = false
    private val afterCommit = mutableListOf<() -> Unit>()
    override fun <T> executeInTransaction(block: () -> T): T {
      events.add("begin")
      active = true
      try {
        val result = block()
        events.add("commit")
        afterCommit.forEach { it() }
        return result
      } catch (e: Throwable) {
        events.add("rollback")
        throw e
      } finally {
        active = false
        afterCommit.clear()
      }
    }

    override fun afterCommitOrNow(block: () -> Unit) {
      if (active) {
        afterCommit.add(block)
      } else {
        block()
      }
    }
  }

  private fun registrar(
    configuration: ProcessEngineWorkerConfiguration = ProcessEngineWorkerConfiguration(),
    executor: TransactionalExecutor = TransactionalExecutor.NONE,
    idempotencyRegistry: InMemoryIdempotencyRegistry = InMemoryIdempotencyRegistry(),
  ) = ProcessEngineWorkerRegistrar(
    configuration,
    taskSubscriptionApi,
    taskCompletionApi,
    variableConverter,
    ParameterResolver.builder().build(),
    ResultResolver.builder().build(),
    executor,
    metrics,
    idempotencyRegistry
  )

  private fun subscribedCommand(): SubscribeForTaskCmd {
    val captor = argumentCaptor<SubscribeForTaskCmd>()
    verify(taskSubscriptionApi).subscribeForTask(captor.capture())
    return captor.firstValue
  }

  private fun task(retries: Int? = null) = TaskInformation(
    taskId = UUID.randomUUID().toString(),
    meta = buildMap {
      put(CommonRestrictions.PROCESS_INSTANCE_ID, UUID.randomUUID().toString())
      if (retries != null) put(TaskInformation.RETRIES, retries.toString())
    }
  )

  @Test
  fun `subscribes with topic, variables and restrictions and completes with returned payload`() {
    class Worker {
      @ProcessEngineWorker(topic = "my.topic", lockDuration = 5000)
      fun work(@Variable("order") order: String, info: TaskInformation): Map<String, Any> = mapOf("shipped" to order.uppercase())
    }

    registrar().registerWorkers(Worker())
    val cmd = subscribedCommand()
    assertThat(cmd.taskDescriptionKey).isEqualTo("my.topic")
    assertThat(cmd.taskType).isEqualTo(TaskType.EXTERNAL)
    assertThat(cmd.payloadDescription).containsExactly("order")
    assertThat(cmd.restrictions[CommonRestrictions.WORKER_LOCK_DURATION_IN_MILLISECONDS]).isEqualTo("5000")

    val taskInformation = task()
    cmd.action.accept(taskInformation, mapOf("order" to "abc"))

    val completion = argumentCaptor<CompleteTaskCmd>()
    verify(taskCompletionApi).completeTask(completion.capture())
    assertThat(completion.firstValue.taskId).isEqualTo(taskInformation.taskId)
    assertThat(completion.firstValue.get()).containsEntry("shipped", "ABC")
    verify(metrics).taskReceived("my.topic")
    verify(metrics).taskCompleted("my.topic")
  }

  @Test
  fun `does not auto-complete if switched off`() {
    class Worker {
      @ProcessEngineWorker(autoComplete = false)
      fun work(): Map<String, Any> = mapOf()
    }

    registrar().registerWorkers(Worker())
    subscribedCommand().action.accept(task(), mapOf())
    verify(taskCompletionApi, never()).completeTask(any())
  }

  @Test
  fun `reports bpmn error thrown by the worker`() {
    class Worker {
      @ProcessEngineWorker("topic")
      fun work() {
        throw BpmnErrorOccurred(message = "boom", errorCode = "E1", payload = mapOf("a" to 1))
      }
    }

    registrar().registerWorkers(Worker())
    subscribedCommand().action.accept(task(), mapOf())

    val captor = argumentCaptor<CompleteTaskByErrorCmd>()
    verify(taskCompletionApi).completeTaskByError(captor.capture())
    assertThat(captor.firstValue.errorCode).isEqualTo("E1")
    assertThat(captor.firstValue.errorMessage).isEqualTo("boom")
    assertThat(captor.firstValue.get()).containsEntry("a", 1)
    verify(metrics).taskCompletedByError("topic")
  }

  @Test
  fun `reports bpmn error thrown inside a transaction`() {
    class Worker {
      @ProcessEngineWorker("topic")
      @jakarta.transaction.Transactional
      fun work() {
        throw BpmnErrorOccurred(message = "boom", errorCode = "E1")
      }
    }

    val executor = RecordingTransactionalExecutor()
    registrar(executor = executor).registerWorkers(Worker())
    subscribedCommand().action.accept(task(), mapOf())

    assertThat(executor.events).containsExactly("begin", "rollback")
    verify(taskCompletionApi).completeTaskByError(any())
  }

  @Test
  fun `fails task with decremented retries on exception`() {
    class Worker {
      @ProcessEngineWorker("topic")
      fun work() {
        throw IllegalStateException("broken")
      }
    }

    registrar().registerWorkers(Worker())
    subscribedCommand().action.accept(task(retries = 3), mapOf())

    val captor = argumentCaptor<FailTaskCmd>()
    verify(taskCompletionApi).failTask(captor.capture())
    assertThat(captor.firstValue.reason).isEqualTo("broken")
    assertThat(captor.firstValue.retryCount).isEqualTo(2)
    assertThat(captor.firstValue.retryBackoff).isNull()
    verify(metrics).taskFailed("topic")
  }

  @Test
  fun `fails task with retries from fail job exception`() {
    class Worker {
      @ProcessEngineWorker("topic")
      fun work() {
        throw FailJobException(message = "later", retryCount = 7, retryBackoff = Duration.ofSeconds(5))
      }
    }

    registrar().registerWorkers(Worker())
    subscribedCommand().action.accept(task(retries = 3), mapOf())

    val captor = argumentCaptor<FailTaskCmd>()
    verify(taskCompletionApi).failTask(captor.capture())
    assertThat(captor.firstValue.retryCount).isEqualTo(7)
    assertThat(captor.firstValue.retryBackoff).isEqualTo(Duration.ofSeconds(5))
  }

  @Test
  fun `unwraps exceptions wrapped by the transactional executor`() {
    class Worker {
      @ProcessEngineWorker("topic")
      @jakarta.transaction.Transactional
      fun work() {
        throw BpmnErrorOccurred(message = "boom", errorCode = "E1")
      }
    }

    val wrappingExecutor = object : TransactionalExecutor {
      override fun <T> executeInTransaction(block: () -> T): T = try {
        block()
      } catch (e: Exception) {
        throw java.lang.reflect.UndeclaredThrowableException(InvocationTargetException(e))
      }

      override fun afterCommitOrNow(block: () -> Unit) = block()
    }
    registrar(executor = wrappingExecutor).registerWorkers(Worker())
    subscribedCommand().action.accept(task(), mapOf())
    verify(taskCompletionApi).completeTaskByError(any())
    verify(taskCompletionApi, never()).failTask(any())
  }

  @Test
  fun `completes transactional worker after commit by default`() {
    class Worker {
      @ProcessEngineWorker("topic")
      @jakarta.transaction.Transactional
      fun work(): Map<String, Any> = mapOf("x" to 1)
    }

    val executor = RecordingTransactionalExecutor()
    whenever(taskCompletionApi.completeTask(any())).thenAnswer {
      executor.events.add("complete")
      CompletableFuture.completedFuture(Empty)
    }
    registrar(executor = executor).registerWorkers(Worker())
    subscribedCommand().action.accept(task(), mapOf())
    assertThat(executor.events).containsExactly("begin", "commit", "complete")
  }

  @Test
  fun `completes transactional worker before commit if configured`() {
    class Worker {
      @ProcessEngineWorker("topic")
      @jakarta.transaction.Transactional
      fun work(): Map<String, Any> = mapOf("x" to 1)
    }

    val executor = RecordingTransactionalExecutor()
    whenever(taskCompletionApi.completeTask(any())).thenAnswer {
      executor.events.add("complete")
      CompletableFuture.completedFuture(Empty)
    }
    registrar(configuration = ProcessEngineWorkerConfiguration(completeTasksBeforeCommit = true), executor = executor).registerWorkers(Worker())
    subscribedCommand().action.accept(task(), mapOf())
    assertThat(executor.events).containsExactly("begin", "complete", "commit")
  }

  @Test
  fun `completion annotation overrules configuration`() {
    class Worker {
      @ProcessEngineWorker("topic", completion = ProcessEngineWorker.Completion.BEFORE_COMMIT)
      @jakarta.transaction.Transactional
      fun work(): Map<String, Any> = mapOf("x" to 1)
    }

    val executor = RecordingTransactionalExecutor()
    whenever(taskCompletionApi.completeTask(any())).thenAnswer {
      executor.events.add("complete")
      CompletableFuture.completedFuture(Empty)
    }
    registrar(executor = executor).registerWorkers(Worker())
    subscribedCommand().action.accept(task(), mapOf())
    assertThat(executor.events).containsExactly("begin", "complete", "commit")
  }

  @Test
  fun `registers the result in the idempotency registry after commit and skips re-invocation`() {
    var invocations = 0

    class Worker {
      @ProcessEngineWorker("topic", autoComplete = false)
      @jakarta.transaction.Transactional
      fun work(): Map<String, Any> {
        invocations++
        return mapOf("x" to invocations)
      }
    }

    val executor = RecordingTransactionalExecutor()
    val registry = InMemoryIdempotencyRegistry()
    registrar(executor = executor, idempotencyRegistry = registry).registerWorkers(Worker())
    val taskInformation = task()
    val cmd = subscribedCommand()
    cmd.action.accept(taskInformation, mapOf())
    cmd.action.accept(taskInformation, mapOf())

    assertThat(invocations).isEqualTo(1)
    assertThat(registry.getTaskResult(taskInformation)).containsEntry("x", 1)
  }

  @Test
  fun `does not register the result in the idempotency registry on rollback`() {
    class Worker {
      @ProcessEngineWorker("topic")
      @jakarta.transaction.Transactional
      fun work(): Map<String, Any> = throw IllegalStateException("broken")
    }

    val executor = RecordingTransactionalExecutor()
    val registry = InMemoryIdempotencyRegistry()
    registrar(executor = executor, idempotencyRegistry = registry).registerWorkers(Worker())
    val taskInformation = task()
    subscribedCommand().action.accept(taskInformation, mapOf())

    assertThat(registry.getTaskResult(taskInformation)).isNull()
  }

  @Test
  fun `invokes the method on the passed bean instance so interception applies`() {
    open class Worker {
      var calls = 0

      @ProcessEngineWorker("topic")
      open fun work(): Map<String, Any> {
        calls++
        return mapOf()
      }
    }

    class Proxy : Worker() {
      var intercepted = 0
      override fun work(): Map<String, Any> {
        intercepted++
        return super.work()
      }
    }

    val proxy = Proxy()
    registrar().registerWorkers(proxy, targetClass = Worker::class.java)
    subscribedCommand().action.accept(task(), mapOf())
    assertThat(proxy.intercepted).isEqualTo(1)
    assertThat(proxy.calls).isEqualTo(1)
  }
}
