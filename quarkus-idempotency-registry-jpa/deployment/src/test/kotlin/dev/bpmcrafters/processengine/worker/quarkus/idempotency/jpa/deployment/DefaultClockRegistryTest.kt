package dev.bpmcrafters.processengine.worker.quarkus.idempotency.jpa.deployment

import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.QuarkusExtensionTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

class DefaultClockRegistryTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest()
      .withApplicationRoot { jar -> jar.addClass(RegistryTestData::class.java) }
      .overrideConfigKey("quarkus.datasource.db-kind", "h2")
      .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:default-clock;DB_CLOSE_DELAY=-1")
      .overrideConfigKey("quarkus.hibernate-orm.schema-management.strategy", "drop-and-create")
  }

  @Inject lateinit var registry: IdempotencyRegistry
  @Inject lateinit var entityManager: EntityManager

  @Test
  fun usesUtcClockWithoutRequiringAnApplicationClockBean() {
    registry.register(RegistryTestData.task("clock", "process"), mapOf("value" to true))
    QuarkusTransaction.requiringNew().run {
      assertThat(entityManager.createQuery("select e.createdAt from WorkerTaskLogEntry e", Instant::class.java)
        .singleResult).isNotNull()
    }
  }
}
