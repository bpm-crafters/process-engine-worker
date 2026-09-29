package dev.bpmcrafters.processengine.worker.quarkus.deployment;

import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry;
import dev.bpmcrafters.processengine.worker.idempotency.InMemoryIdempotencyRegistry;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.InMemoryTaskSubscriptionApi;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.RecordingServiceTaskCompletionApi;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.TestSupport;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.CustomBeans;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.CustomBeansWorker;
import dev.bpmcrafters.processengine.worker.registrar.ParameterResolver;
import dev.bpmcrafters.processengine.worker.registrar.ResultResolver;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario 7: application-provided beans replace the default beans.
 */
class CustomBeansTest {

  @RegisterExtension
  static final QuarkusExtensionTest TEST = TestSupport.withoutJpa(new QuarkusExtensionTest()
    .setArchiveProducer(() -> TestSupport.archive(CustomBeans.class, CustomBeansWorker.class)));

  @Inject
  InMemoryTaskSubscriptionApi subscriptionApi;

  @Inject
  RecordingServiceTaskCompletionApi completionApi;

  @Test
  void customBeansReplaceDefaults() {
    assertThat(Arc.container().listAll(IdempotencyRegistry.class)).hasSize(1);
    assertThat(ClientProxy.unwrap(Arc.container().instance(IdempotencyRegistry.class).get())).isInstanceOf(InMemoryIdempotencyRegistry.class);
    assertThat(Arc.container().listAll(ParameterResolver.class)).hasSize(1);
    assertThat(Arc.container().listAll(ResultResolver.class)).hasSize(1);

    subscriptionApi.deliver("custom", "task-1", Map.of());
    assertThat(completionApi.getCompleted()).hasSize(1);
    assertThat(completionApi.getCompleted().get(0).get()).isEqualTo(Map.of("text", "hello task-1"));
  }
}
