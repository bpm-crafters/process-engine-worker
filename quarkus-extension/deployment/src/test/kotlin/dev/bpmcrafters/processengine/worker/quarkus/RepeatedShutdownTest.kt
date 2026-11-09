package dev.bpmcrafters.processengine.worker.quarkus

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.task.*
import io.quarkus.arc.Arc
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.test.QuarkusExtensionTest
import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.Dependent
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

class RepeatedShutdownTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot {
      it.addClasses(State::class.java, Workers::class.java, Engine::class.java, Handle::class.java, Metadata::class.java)
    }
  }

  @Inject
  lateinit var state: State

  @Test
  fun repeatedExternalShutdownWaitsForWorkerAndAsyncUnsubscribeAfterWorkerInitiatedStop() {
    val executor = Executors.newFixedThreadPool(3)
    try {
      val delivery = executor.submit { state.deliver("accepted") }
      state.workerStopReturned.get(5, TimeUnit.SECONDS)
      state.unsubscribeStarted.get(5, TimeUnit.SECONDS)
      assertThat(state.invocations).hasValue(1)
      assertThat(state.disposed).hasValue(0)
      assertThatThrownBy { state.deliver("rejected") }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessage("Worker runtime is closed")
      assertThat(state.invocations).hasValue(1)

      val started = CountDownLatch(2)
      val shutdowns = (1..2).map {
        executor.submit {
          started.countDown()
          state.shutdownRuntime()
        }
      }
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()
      assertBothWaiting(shutdowns)
      assertThat(state.disposed).hasValue(0)

      state.releaseWorker.complete(null)
      delivery.get(5, TimeUnit.SECONDS)
      assertThat(state.completed).containsExactly("accepted")
      assertBothWaiting(shutdowns)
      assertThat(state.disposed).hasValue(0)
      assertThat(state.unsubscribes).hasValue(1)

      state.unsubscribeResult.complete(Empty)
      shutdowns.forEach { it.get(5, TimeUnit.SECONDS) }
      state.allDisposed.get(5, TimeUnit.SECONDS)
      state.shutdownRuntime()
      assertThat(state.disposed).hasValue(2)
      assertThat(state.unsubscribes).hasValue(1)
      assertThat(state.closed).hasValue(0)
      assertThat(state.failed).isEmpty()
    } finally {
      state.releaseWorker.complete(null)
      state.unsubscribeResult.complete(Empty)
      executor.shutdownNow()
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
    }
  }

  private fun assertBothWaiting(shutdowns: List<Future<*>>) {
    assertAll(
      { assertThatThrownBy { shutdowns[0].get(150, TimeUnit.MILLISECONDS) }.isInstanceOf(TimeoutException::class.java) },
      { assertThatThrownBy { shutdowns[1].get(150, TimeUnit.MILLISECONDS) }.isInstanceOf(TimeoutException::class.java) }
    )
  }

  class Handle : TaskSubscription
  class Metadata : MetaInfo

  @Singleton
  class State {
    lateinit var command: SubscribeForTaskCmd
    val workerStopReturned = CompletableFuture<Void>()
    val releaseWorker = CompletableFuture<Void>()
    val unsubscribeStarted = CompletableFuture<Void>()
    val unsubscribeResult = CompletableFuture<Empty>()
    val allDisposed = CompletableFuture<Void>()
    val invocations = AtomicInteger()
    val unsubscribes = AtomicInteger()
    val disposed = AtomicInteger()
    val closed = AtomicInteger()
    val completed = CopyOnWriteArrayList<String>()
    val failed = CopyOnWriteArrayList<String>()

    fun deliver(id: String) {
      command.action.accept(TaskInformation(id, mapOf("processInstanceId" to "process")), emptyMap())
    }

    fun shutdownRuntime() {
      // The fixture and runtime have different Quarkus classloaders.
      val method = WorkerRuntime::class.java.getDeclaredMethod("shutdown", ShutdownEvent::class.java)
      method.isAccessible = true
      method.invoke(Arc.container().instance(WorkerRuntime::class.java).get(), ShutdownEvent())
    }
  }

  @Singleton
  class Workers {
    @Inject
    lateinit var state: State

    @ProcessEngineWorker(topic = "repeated-shutdown")
    fun work(): Map<String, Any?> {
      state.invocations.incrementAndGet()
      state.shutdownRuntime()
      state.shutdownRuntime()
      state.workerStopReturned.complete(null)
      state.releaseWorker.get(10, TimeUnit.SECONDS)
      return mapOf("value" to "finished")
    }
  }

  @Dependent
  class Engine : TaskSubscriptionApi, ServiceTaskCompletionApi, AutoCloseable {
    @Inject
    lateinit var state: State
    private val disposals = AtomicInteger()

    override fun subscribeForTask(command: SubscribeForTaskCmd): CompletableFuture<TaskSubscription> {
      state.command = command
      return CompletableFuture.completedFuture(Handle())
    }

    override fun unsubscribe(command: UnsubscribeFromTaskCmd): CompletableFuture<Empty> {
      state.unsubscribes.incrementAndGet()
      state.unsubscribeStarted.complete(null)
      return state.unsubscribeResult
    }

    override fun getSupportedRestrictions(): Set<String> = emptySet()
    override fun meta(instance: MetaInfoAware): MetaInfo = Metadata()
    override fun completeTask(command: CompleteTaskCmd): CompletableFuture<Empty> {
      assertThat(state.disposed).hasValue(0)
      state.completed.add(command.taskId)
      return CompletableFuture.completedFuture(Empty)
    }

    override fun completeTaskByError(command: CompleteTaskByErrorCmd): CompletableFuture<Empty> =
      throw AssertionError("Unexpected business error")

    override fun failTask(command: FailTaskCmd): CompletableFuture<Empty> {
      state.failed.add(command.taskId)
      return CompletableFuture.completedFuture(Empty)
    }

    @PreDestroy
    fun dispose() {
      assertThat(disposals.incrementAndGet()).isEqualTo(1)
      if (state.disposed.incrementAndGet() == 2) state.allDisposed.complete(null)
    }

    override fun close() {
      state.closed.incrementAndGet()
    }
  }
}
