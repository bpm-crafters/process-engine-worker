package dev.bpmcrafters.processengine.worker.quarkus.idempotency.jpa.deployment

import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.idempotency.LocalInMemoryIdempotencyRegistry
import io.quarkus.test.QuarkusExtensionTest
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class CustomRegistryOverrideTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest()
      .withApplicationRoot { jar -> jar.addClasses(CustomRegistry::class.java, RegistryTestData::class.java) }
      .overrideConfigKey("quarkus.datasource.db-kind", "h2")
      .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:override;DB_CLOSE_DELAY=-1")
      .overrideConfigKey("quarkus.hibernate-orm.schema-management.strategy", "drop-and-create")
  }

  @Inject lateinit var registry: IdempotencyRegistry

  @Test
  fun applicationBeanOverridesBothJpaAndNoOpDefaults() {
    assertThat(registry).isInstanceOf(LocalInMemoryIdempotencyRegistry::class.java)
    val task = RegistryTestData.task("custom", "process")
    registry.register(task, mapOf("custom" to true))
    assertThat(registry.getTaskResult(task)).containsEntry("custom", true)
  }

  @Singleton
  class CustomRegistry {
    @Produces
    @Singleton
    fun registry(): IdempotencyRegistry = LocalInMemoryIdempotencyRegistry()
  }
}
