package dev.bpmcrafters.processengine.worker.quarkus.deployment

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import io.quarkus.test.QuarkusExtensionTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class UnmanagedWorkerTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot { it.addClass(UnmanagedWorker::class.java) }
      .assertException { assertThat(it).hasStackTraceContaining("Annotated worker has no supported CDI-managed bean") }
  }

  @Test
  fun explainsMissingCdiScopeRatherThanSilentlyIgnoringWorkers() {
  }

  class UnmanagedWorker {
    @ProcessEngineWorker
    fun work() {
    }
  }
}
