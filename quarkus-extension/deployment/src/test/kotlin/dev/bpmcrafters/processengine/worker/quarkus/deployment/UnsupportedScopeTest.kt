package dev.bpmcrafters.processengine.worker.quarkus.deployment

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import io.quarkus.test.QuarkusExtensionTest
import jakarta.enterprise.context.Dependent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class UnsupportedScopeTest {
  companion object {
    @JvmField @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot { it.addClass(DependentWorker::class.java) }
      .assertException { assertThat(it).hasStackTraceContaining("Workers require @ApplicationScoped or @Singleton") }
  }
  @Test fun explainsUnsupportedScope() {}
  @Dependent class DependentWorker { @ProcessEngineWorker fun work() {} }
}
