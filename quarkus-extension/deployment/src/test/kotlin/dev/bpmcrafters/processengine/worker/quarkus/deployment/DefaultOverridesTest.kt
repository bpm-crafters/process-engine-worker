package dev.bpmcrafters.processengine.worker.quarkus.deployment

import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.idempotency.NoOpIdempotencyRegistry
import dev.bpmcrafters.processengine.worker.registrar.ParameterResolver
import dev.bpmcrafters.processengine.worker.registrar.ResultResolver
import dev.bpmcrafters.processengine.worker.registrar.VariableConverter
import io.quarkus.test.QuarkusExtensionTest
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class DefaultOverridesTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot { it.addClasses(Overrides::class.java, Overrides.Companion::class.java, CustomConverter::class.java) }
  }

  @Inject
  lateinit var converter: VariableConverter
  @Inject
  lateinit var parameters: ParameterResolver
  @Inject
  lateinit var results: ResultResolver
  @Inject
  lateinit var registry: IdempotencyRegistry

  @Test
  fun applicationProducersReplaceAllDefaults() {
    assertThat(converter).isInstanceOf(CustomConverter::class.java)
    assertThat(parameters).isSameAs(Overrides.PARAMETERS)
    assertThat(results).isSameAs(Overrides.RESULTS)
    assertThat(registry).isSameAs(Overrides.REGISTRY)
  }

  @Singleton
  class Overrides {
    companion object {
      val PARAMETERS = ParameterResolver.builder().build()
      val RESULTS = ResultResolver.builder().build()
      val REGISTRY: IdempotencyRegistry = NoOpIdempotencyRegistry()
    }

    @Produces
    @Singleton
    fun converter(): VariableConverter = CustomConverter()
    @Produces
    @Singleton
    fun parameters(): ParameterResolver = PARAMETERS
    @Produces
    @Singleton
    fun results(): ResultResolver = RESULTS
    @Produces
    @Singleton
    fun registry(): IdempotencyRegistry = REGISTRY
  }

  class CustomConverter : VariableConverter {
    override fun <T : Any> mapToType(value: Any?, type: Class<T>): T = type.cast(value)
  }
}
