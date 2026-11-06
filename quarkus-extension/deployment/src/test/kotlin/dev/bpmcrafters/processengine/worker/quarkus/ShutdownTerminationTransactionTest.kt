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
import jakarta.transaction.Status
import jakarta.transaction.Synchronization
import jakarta.transaction.TransactionSynchronizationRegistry
import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ShutdownTerminationTransactionTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot {
      it.addClasses(
        State::class.java, Engine::class.java, Workers::class.java,
        CommitMarker::class.java, Handle::class.java, Metadata::class.java
      )
    }
  }

  @Inject
  lateinit var state: State

  @Test
  fun remoteSuccessAndTerminationDuringShutdownStillCommitLocalBusinessTransaction() {
    val executor = Executors.newFixedThreadPool(2)
    try {
      val delivery = executor.submit {
        state.command.action.accept(
          TaskInformation("task", mapOf("processInstanceId" to "process")), emptyMap()
        )
      }
      state.workerEntered.get(5, TimeUnit.SECONDS)
      val shutdown = executor.submit {
        // The fixture and runtime have different Quarkus classloaders.
        val method = WorkerRuntime::class.java.getDeclaredMethod("shutdown", ShutdownEvent::class.java)
        method.isAccessible = true
        method.invoke(Arc.container().instance(WorkerRuntime::class.java).get(), ShutdownEvent())
      }
      // Unsubscribe is invoked only after the delivery gate has closed.
      state.unsubscribeStarted.get(5, TimeUnit.SECONDS)
      assertThat(shutdown.isDone).isFalse()
      assertThat(state.disposed).hasValue(0)
      state.releaseWorker.complete(null)
      delivery.get(5, TimeUnit.SECONDS)
      shutdown.get(5, TimeUnit.SECONDS)
      assertThat(state.completed).containsExactly("task")
      assertThat(state.committed).containsExactly("business-write")
      assertThat(state.outcome.get(5, TimeUnit.SECONDS)).isEqualTo(Status.STATUS_COMMITTED)
      assertThat(state.failed).isEmpty()
      state.allDisposed.get(5, TimeUnit.SECONDS)
      assertThat(state.disposed).hasValue(2)
      assertThat(state.events.indexOf("termination-returned")).isLessThan(state.events.indexOf("commit"))
      assertThat(state.events.indexOf("commit")).isLessThan(state.events.indexOf("dispose"))
      // Adapters may also notify termination after all worker references have been released.
      state.command.termination.accept(TaskInformation("task", emptyMap()))
      assertThat(state.closed).hasValue(0)
    } finally {
      state.releaseWorker.complete(null)
      executor.shutdownNow()
      executor.awaitTermination(5, TimeUnit.SECONDS)
    }
  }

  class Handle : TaskSubscription
  class Metadata : MetaInfo

  @Singleton
  class State {
    lateinit var command: SubscribeForTaskCmd
    val workerEntered = CompletableFuture<Void>()
    val releaseWorker = CompletableFuture<Void>()
    val unsubscribeStarted = CompletableFuture<Void>()
    val outcome = CompletableFuture<Int>()
    val allDisposed = CompletableFuture<Void>()
    val completed = CopyOnWriteArrayList<String>()
    val committed = CopyOnWriteArrayList<String>()
    val failed = CopyOnWriteArrayList<String>()
    val events = CopyOnWriteArrayList<String>()
    val disposed = AtomicInteger()
    val closed = AtomicInteger()
  }

  class CommitMarker(private val state: State) : Synchronization {
    override fun beforeCompletion() = Unit
    override fun afterCompletion(status: Int) {
      if (status == Status.STATUS_COMMITTED) {
        state.committed.add("business-write")
        state.events.add("commit")
      }
      state.outcome.complete(status)
    }
  }

  @Singleton
  class Workers {
    @Inject
    lateinit var state: State
    @Inject
    lateinit var transactions: TransactionSynchronizationRegistry

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    @ProcessEngineWorker(topic = "shutdown", completion = ProcessEngineWorker.Completion.BEFORE_COMMIT)
    fun work(): Map<String, Any?> {
      transactions.registerInterposedSynchronization(CommitMarker(state))
      state.workerEntered.complete(null)
      state.releaseWorker.get(5, TimeUnit.SECONDS)
      return mapOf("value" to "business-write")
    }
  }

  @Dependent
  class Engine : TaskSubscriptionApi, ServiceTaskCompletionApi, AutoCloseable {
    @Inject
    lateinit var state: State
    override fun subscribeForTask(command: SubscribeForTaskCmd): CompletableFuture<TaskSubscription> {
      state.command = command
      return CompletableFuture.completedFuture(Handle())
    }

    override fun unsubscribe(command: UnsubscribeFromTaskCmd): CompletableFuture<Empty> {
      state.unsubscribeStarted.complete(null)
      return CompletableFuture.completedFuture(Empty)
    }

    override fun getSupportedRestrictions(): Set<String> = emptySet()
    override fun meta(instance: MetaInfoAware): MetaInfo = Metadata()
    override fun completeTask(command: CompleteTaskCmd): CompletableFuture<Empty> {
      assertThat(state.disposed).hasValue(0)
      // Match the adapter: remote state advances BEFORE the subscription callback runs.
      state.completed.add(command.taskId)
      state.command.termination.accept(TaskInformation(command.taskId, emptyMap()))
      state.events.add("termination-returned")
      assertThat(state.disposed).hasValue(0)
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
      state.events.add("dispose")
      if (state.disposed.incrementAndGet() == 2) state.allDisposed.complete(null)
    }

    override fun close() {
      state.closed.incrementAndGet()
    }
  }
}
