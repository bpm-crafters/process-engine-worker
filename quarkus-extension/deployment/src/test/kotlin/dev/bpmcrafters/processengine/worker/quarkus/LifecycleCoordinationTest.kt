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
import jakarta.transaction.TransactionManager
import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LifecycleCoordinationTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot {
      it.addClasses(
        State::class.java, Engine::class.java,
        Workers::class.java, Handle::class.java, Metadata::class.java
      )
    }
  }

  @Inject
  lateinit var state: State
  @Inject
  lateinit var transactions: TransactionManager

  @Test
  fun shutdownInsideDeliveryCancelsUncommittedCallbacksAndDisposesOnlyAfterAsyncUnsubscribe() {
    transactions.begin()
    try {
      val ambient = transactions.transaction
      state.deliver("ambient", "ambient-task")
      assertThat(state.failed).isEmpty()
      assertThat(state.completed).isEmpty()
      state.deliver("stop", "stop-task")
      assertThat(state.failed).isEmpty()
      state.stopReturned.get(5, TimeUnit.SECONDS)
      assertThat(transactions.transaction).isSameAs(ambient)
      assertThat(ambient.status).isEqualTo(Status.STATUS_ACTIVE)
      assertThat(state.completed).containsExactly("stop-task")
      assertThat(state.disposed).hasValue(0)
      assertThat(state.unsubscribes).hasValue(2)
      state.unsubscribeResult.complete(Empty)
      assertThat(state.disposed).hasValue(0)
      transactions.commit()
      state.allDisposed.get(5, TimeUnit.SECONDS)
      assertThat(state.disposed).hasValue(2)
      assertThat(state.closed).hasValue(0)
      assertThat(state.completed).containsExactly("stop-task")
      assertThat(state.failed).isEmpty()
      assertThat(state.events.indexOf("complete:stop-task")).isLessThan(state.events.indexOf("dispose"))
    } finally {
      state.unsubscribeResult.complete(Empty)
      if (transactions.transaction != null) transactions.rollback()
    }
  }

  class Handle : TaskSubscription
  class Metadata : MetaInfo

  @Singleton
  class State {
    val commands = ConcurrentHashMap<String, SubscribeForTaskCmd>()
    val events = CopyOnWriteArrayList<String>()
    val completed = CopyOnWriteArrayList<String>()
    val failed = CopyOnWriteArrayList<String>()
    val disposed = AtomicInteger()
    val closed = AtomicInteger()
    val unsubscribes = AtomicInteger()
    val unsubscribeResult = CompletableFuture<Empty>()
    val stopReturned = CompletableFuture<Void>()
    val allDisposed = CompletableFuture<Void>()
    fun deliver(topic: String, id: String) {
      commands[topic]!!.action.accept(TaskInformation(id, mapOf("processInstanceId" to "process")), emptyMap())
    }
  }

  @Singleton
  class Workers {
    @Inject
    lateinit var state: State

    @Transactional
    @ProcessEngineWorker(topic = "ambient")
    fun ambient(): Map<String, Any?> = mapOf("value" to "ambient")

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    @ProcessEngineWorker(topic = "stop", completion = ProcessEngineWorker.Completion.BEFORE_COMMIT)
    @Throws(ReflectiveOperationException::class)
    fun stop(): Map<String, Any?> {
      // Fixture and runtime use different Quarkus classloaders, so package access is not shared.
      val shutdown = WorkerRuntime::class.java.getDeclaredMethod("shutdown", ShutdownEvent::class.java)
      shutdown.isAccessible = true
      shutdown.invoke(Arc.container().instance(WorkerRuntime::class.java).get(), ShutdownEvent())
      state.stopReturned.complete(null)
      return mapOf("value" to "stop")
    }
  }

  @Dependent
  class Engine : TaskSubscriptionApi, ServiceTaskCompletionApi, AutoCloseable {
    @Inject
    lateinit var state: State
    override fun subscribeForTask(command: SubscribeForTaskCmd): CompletableFuture<TaskSubscription> {
      state.commands[requireNotNull(command.taskDescriptionKey)] = command
      return CompletableFuture.completedFuture(Handle())
    }

    override fun unsubscribe(command: UnsubscribeFromTaskCmd): CompletableFuture<Empty> {
      state.unsubscribes.incrementAndGet()
      return state.unsubscribeResult
    }

    override fun getSupportedRestrictions(): Set<String> = emptySet()
    override fun meta(instance: MetaInfoAware): MetaInfo = Metadata()
    override fun completeTask(command: CompleteTaskCmd): CompletableFuture<Empty> {
      state.completed.add(command.taskId)
      state.events.add("complete:${command.taskId}")
      return CompletableFuture.completedFuture(Empty)
    }

    override fun completeTaskByError(command: CompleteTaskByErrorCmd): CompletableFuture<Empty> = throw AssertionError("Unexpected business error")
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
