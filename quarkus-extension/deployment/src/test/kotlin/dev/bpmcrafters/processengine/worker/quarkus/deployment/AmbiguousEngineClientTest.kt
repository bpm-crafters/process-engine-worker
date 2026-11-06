package dev.bpmcrafters.processengine.worker.quarkus.deployment

import io.quarkus.test.QuarkusExtensionTest
import jakarta.inject.Singleton
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class AmbiguousEngineClientTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest()
      .withApplicationRoot { it.addClasses(DisabledWorkerTest.UnusedWorker::class.java, WorkerExtensionTest.Engine::class.java, AnotherEngine::class.java) }
      .assertException { assertThat(it).hasStackTraceContaining("Active process engine workers require exactly one CDI bean of type dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi") }
  }

  @Test
  fun explainsAmbiguousEngineClient() {
  }

  @Singleton
  class AnotherEngine : WorkerExtensionTest.Engine()
}
