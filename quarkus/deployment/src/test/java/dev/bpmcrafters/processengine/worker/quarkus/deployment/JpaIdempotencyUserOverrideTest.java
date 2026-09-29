package dev.bpmcrafters.processengine.worker.quarkus.deployment;

import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry;
import dev.bpmcrafters.processengine.worker.idempotency.InMemoryIdempotencyRegistry;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.TestSupport;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.CustomBeans;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.IdempotentWorker;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3b: an application-provided registry still wins over the JPA default.
 */
class JpaIdempotencyUserOverrideTest {

  @RegisterExtension
  static final QuarkusExtensionTest TEST = TestSupport.withJpa(new QuarkusExtensionTest()
    .setArchiveProducer(() -> TestSupport.archive(CustomBeans.class, IdempotentWorker.class)), "idempotency-override");

  @Test
  void userRegistryWins() {
    assertThat(Arc.container().listAll(IdempotencyRegistry.class)).hasSize(1);
    assertThat(ClientProxy.unwrap(Arc.container().instance(IdempotencyRegistry.class).get())).isInstanceOf(InMemoryIdempotencyRegistry.class);
  }
}
