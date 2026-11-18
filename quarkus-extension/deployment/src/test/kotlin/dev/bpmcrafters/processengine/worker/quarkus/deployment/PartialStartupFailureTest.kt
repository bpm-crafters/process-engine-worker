package dev.bpmcrafters.processengine.worker.quarkus.deployment

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.task.SubscribeForTaskCmd
import dev.bpmcrafters.processengineapi.task.TaskSubscription
import dev.bpmcrafters.processengineapi.task.UnsubscribeFromTaskCmd
import io.quarkus.test.QuarkusExtensionTest
import jakarta.annotation.PreDestroy
import jakarta.annotation.Priority
import jakarta.enterprise.context.Dependent
import jakarta.enterprise.inject.Alternative
import jakarta.inject.Singleton
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.CompletableFuture

class PartialStartupFailureTest {
  companion object {
    private const val UNSUBSCRIBED = "worker-test.partial.unsubscribed"
    private const val DESTROYED = "worker-test.partial.destroyed"

    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest().setBeforeAllCustomizer {
      System.clearProperty(UNSUBSCRIBED)
      System.setProperty(DESTROYED, "0")
    }.withApplicationRoot { it.addClasses(TwoWorkers::class.java, FailingEngine::class.java, WorkerExtensionTest.Engine::class.java, Handle::class.java) }
      .assertException {
        assertThat(it).hasStackTraceContaining("subscription failed")
        assertThat(it).hasStackTraceContaining("cleanup unsubscribe failed")
        assertThat(System.getProperty(UNSUBSCRIBED)).isEqualTo("true")
        assertThat(System.getProperty(DESTROYED)).isEqualTo("2")
      }.setAfterAllCustomizer {
        System.clearProperty(UNSUBSCRIBED)
        System.clearProperty(DESTROYED)
      }
  }

  @Test
  fun unsubscribesEarlierHandleAndReleasesOwnedDependentClients() {
  }

  @Singleton
  class TwoWorkers {
    @ProcessEngineWorker
    fun first() {
    }

    @ProcessEngineWorker
    fun second() {
    }
  }

  @Dependent
  @Alternative
  @Priority(1)
  class FailingEngine : WorkerExtensionTest.Engine() {
    private var subscriptionCount = 0
    override fun subscribeForTask(command: SubscribeForTaskCmd): CompletableFuture<TaskSubscription> {
      if (++subscriptionCount == 2) return CompletableFuture.failedFuture(IllegalStateException("subscription failed"))
      return CompletableFuture.completedFuture(Handle())
    }

    override fun unsubscribe(command: UnsubscribeFromTaskCmd): CompletableFuture<Empty> {
      System.setProperty(UNSUBSCRIBED, "true")
      return CompletableFuture.failedFuture(IllegalStateException("cleanup unsubscribe failed"))
    }

    @PreDestroy
    fun destroyed() {
      System.setProperty(DESTROYED, (System.getProperty(DESTROYED).toInt() + 1).toString())
    }
  }

  class Handle : TaskSubscription
}
