package dev.bpmcrafters.processengine.worker.quarkus.deployment

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.quarkus.WorkerConfig
import io.quarkus.arc.profile.IfBuildProfile
import io.quarkus.arc.profile.UnlessBuildProfile
import io.quarkus.arc.properties.IfBuildProperty
import io.quarkus.arc.properties.UnlessBuildProperty
import io.quarkus.test.QuarkusExtensionTest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Stereotype
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ExcludedConditionalWorkersTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot {
      it.addClasses(
        ProductionWorker::class.java, OutsideTestWorker::class.java, DisabledPropertyWorker::class.java,
        InversePropertyWorker::class.java, MissingPropertyWorker::class.java, MissingInverseWorker::class.java,
        ProductionOnly::class.java, StereotypedWorker::class.java, InvalidExcludedWorker::class.java
      )
    }
      .overrideConfigKey("discovery.enabled", "false")
      .overrideConfigKey("discovery.disabled", "true")
  }

  @Inject
  lateinit var config: WorkerConfig

  @Test
  fun startsWithoutEngineClientsWhenArcExcludesEveryWorker() {
    assertThat(config.enabled()).isTrue()
    assertThat(config.registerProcessWorkers()).isTrue()
  }

  @ApplicationScoped
  @IfBuildProfile("prod")
  class ProductionWorker {
    @ProcessEngineWorker
    fun work(): Map<String, Any?> = emptyMap()
  }

  @ApplicationScoped
  @UnlessBuildProfile("test")
  class OutsideTestWorker {
    @ProcessEngineWorker
    fun work(): Map<String, Any?> = emptyMap()
  }

  @ApplicationScoped
  @IfBuildProperty(name = "discovery.enabled", stringValue = "true")
  class DisabledPropertyWorker {
    @ProcessEngineWorker
    fun work(): Map<String, Any?> = emptyMap()
  }

  @ApplicationScoped
  @UnlessBuildProperty(name = "discovery.disabled", stringValue = "true")
  class InversePropertyWorker {
    @ProcessEngineWorker
    fun work(): Map<String, Any?> = emptyMap()
  }

  @ApplicationScoped
  @IfBuildProperty(name = "discovery.missing", stringValue = "true", enableIfMissing = false)
  class MissingPropertyWorker {
    @ProcessEngineWorker
    fun work(): Map<String, Any?> = emptyMap()
  }

  @ApplicationScoped
  @UnlessBuildProperty(name = "discovery.missing-inverse", stringValue = "true", enableIfMissing = false)
  class MissingInverseWorker {
    @ProcessEngineWorker
    fun work(): Map<String, Any?> = emptyMap()
  }

  @Stereotype
  @ApplicationScoped
  @IfBuildProfile("prod")
  @Retention(AnnotationRetention.RUNTIME)
  @Target(AnnotationTarget.CLASS)
  annotation class ProductionOnly

  @ProductionOnly
  class StereotypedWorker {
    @ProcessEngineWorker
    fun work(): Map<String, Any?> = emptyMap()
  }

  @ApplicationScoped
  @IfBuildProperty(name = "discovery.enabled", stringValue = "true")
  class InvalidExcludedWorker {
    @ProcessEngineWorker
    private fun work(): Map<String, Any?> = emptyMap()
  }
}
