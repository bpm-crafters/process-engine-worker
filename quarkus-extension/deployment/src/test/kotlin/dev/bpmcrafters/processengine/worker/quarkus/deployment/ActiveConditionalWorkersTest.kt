package dev.bpmcrafters.processengine.worker.quarkus.deployment

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import io.quarkus.arc.profile.IfBuildProfile
import io.quarkus.arc.profile.UnlessBuildProfile
import io.quarkus.arc.properties.IfBuildProperty
import io.quarkus.arc.properties.UnlessBuildProperty
import io.quarkus.test.QuarkusExtensionTest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.Dependent
import jakarta.enterprise.inject.Stereotype
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ActiveConditionalWorkersTest {
  companion object {
    @JvmField @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot { it.addClasses(
      TestWorker::class.java, OutsideProductionWorker::class.java, EnabledPropertyWorker::class.java,
      InversePropertyWorker::class.java, MissingPropertyWorker::class.java, MissingInverseWorker::class.java,
      TestOnly::class.java, StereotypedWorker::class.java,
      WorkerIdentityAndMetadataTest.Engine::class.java, WorkerIdentityAndMetadataTest.Handle::class.java,
      WorkerIdentityAndMetadataTest.Metadata::class.java) }
      .overrideConfigKey("discovery.enabled", "true")
      .overrideConfigKey("discovery.disabled", "false")
  }

  @Inject lateinit var engine: WorkerIdentityAndMetadataTest.Engine

  @Test fun registersAndDeliversEveryArcEnabledWorkerExactlyOnce() {
    val topics = listOf("if-profile", "unless-profile", "if-property", "unless-property",
      "missing-if", "missing-unless", "stereotype")
    assertThat(engine.commands.map { it.taskDescriptionKey }).containsExactlyInAnyOrderElementsOf(topics)
    topics.forEach { topic ->
      engine.deliver(topic, emptyMap())
      assertThat(engine.results[topic]).containsExactly(topic)
    }
  }

  @ApplicationScoped @IfBuildProfile("test")
  class TestWorker { @ProcessEngineWorker(topic = "if-profile") fun work(): Map<String, Any?> = mapOf("value" to "if-profile") }
  @ApplicationScoped @UnlessBuildProfile("prod")
  class OutsideProductionWorker { @ProcessEngineWorker(topic = "unless-profile") fun work(): Map<String, Any?> = mapOf("value" to "unless-profile") }
  @ApplicationScoped @IfBuildProperty(name = "discovery.enabled", stringValue = "true")
  class EnabledPropertyWorker { @ProcessEngineWorker(topic = "if-property") fun work(): Map<String, Any?> = mapOf("value" to "if-property") }
  @ApplicationScoped @UnlessBuildProperty(name = "discovery.disabled", stringValue = "true")
  class InversePropertyWorker { @ProcessEngineWorker(topic = "unless-property") fun work(): Map<String, Any?> = mapOf("value" to "unless-property") }
  @ApplicationScoped @IfBuildProperty(name = "discovery.missing", stringValue = "true", enableIfMissing = true)
  class MissingPropertyWorker { @ProcessEngineWorker(topic = "missing-if") fun work(): Map<String, Any?> = mapOf("value" to "missing-if") }
  @ApplicationScoped @UnlessBuildProperty(name = "discovery.missing-inverse", stringValue = "true", enableIfMissing = true)
  class MissingInverseWorker { @ProcessEngineWorker(topic = "missing-unless") fun work(): Map<String, Any?> = mapOf("value" to "missing-unless") }

  @Stereotype @ApplicationScoped @IfBuildProfile("test")
  @Retention(AnnotationRetention.RUNTIME) @Target(AnnotationTarget.CLASS)
  annotation class TestOnly
  @TestOnly
  class StereotypedWorker { @ProcessEngineWorker(topic = "stereotype") fun work(): Map<String, Any?> = mapOf("value" to "stereotype") }
}

class ActiveConditionalWorkerValidationTest {
  companion object {
    @JvmField @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot { it.addClass(InvalidActiveWorker::class.java) }
      .overrideConfigKey("discovery.enabled", "true")
      .assertException { assertThat(it).hasStackTraceContaining("Workers require @ApplicationScoped or @Singleton") }
  }
  @Test fun stillRejectsUnsupportedScopeOnAnEnabledConditionalWorker() {}
  @Dependent @IfBuildProperty(name = "discovery.enabled", stringValue = "true")
  class InvalidActiveWorker { @ProcessEngineWorker fun work(): Map<String, Any?> = emptyMap() }
}
