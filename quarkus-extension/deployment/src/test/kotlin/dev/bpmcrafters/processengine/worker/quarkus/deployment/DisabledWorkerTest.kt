package dev.bpmcrafters.processengine.worker.quarkus.deployment

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.idempotency.NoOpIdempotencyRegistry
import dev.bpmcrafters.processengine.worker.registrar.VariableConverter
import io.quarkus.builder.Version
import io.quarkus.maven.dependency.Dependency
import io.quarkus.test.QuarkusExtensionTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.LocalDate
import java.util.*

class DisabledWorkerTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest()
      .withApplicationRoot { it.addClasses(UnusedWorker::class.java, Payload::class.java) }
      // HTTP's RuntimeInfoProvider can be visible through the parent loader without Micrometer.
      // Bootstrap the complete HTTP extension rather than excluding transitive jars.
      .setForcedDependencies(listOf(Dependency.of("io.quarkus", "quarkus-vertx-http", Version.getVersion())))
      .overrideRuntimeConfigKey("dev.bpm-crafters.process-api.worker.enabled", "false")
  }

  @Inject
  lateinit var registry: IdempotencyRegistry
  @Inject
  lateinit var converter: VariableConverter

  @Test
  fun startsWithoutEngineClientsAndProvidesNoOpIdempotencyRegistry() {
    // No-op metrics without Micrometer are covered by the isolated example's OrderResourceTest.
    assertThat(registry).isInstanceOf(NoOpIdempotencyRegistry::class.java)
  }

  @Test
  fun jacksonDefaultsSupportJavaTimeAndJdk8Payloads() {
    val payload = converter.mapToType(mapOf("date" to "2026-11-05", "name" to "Alice"), Payload::class.java)
    assertThat(payload.date).isEqualTo(LocalDate.of(2026, 11, 5))
    assertThat(payload.name).contains("Alice")
  }

  data class Payload(val date: LocalDate, val name: Optional<String>)

  @Singleton
  class UnusedWorker {
    @ProcessEngineWorker
    fun work() {
    }
  }
}
