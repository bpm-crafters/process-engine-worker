package dev.bpmcrafters.processengine.worker.quarkus.deployment;

import dev.bpmcrafters.processengine.worker.quarkus.ProcessEngineWorkerRegistration;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.InMemoryTaskSubscriptionApi;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.TestSupport;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.SimpleWorker;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusExtensionTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario 4: worker registration switched off.
 */
class WorkerDisabledTest {

  @RegisterExtension
  static final QuarkusExtensionTest TEST = TestSupport.withoutJpa(new QuarkusExtensionTest()
    .setArchiveProducer(() -> TestSupport.archive(SimpleWorker.class))
    .overrideConfigKey("dev.bpm-crafters.process-api.worker.enabled", "false"));

  @Inject
  InMemoryTaskSubscriptionApi subscriptionApi;

  @Inject
  ProcessEngineWorkerRegistration registration;

  @Test
  void nothingIsRegistered() {
    assertThat(subscriptionApi.getSubscriptions()).isEmpty();
    assertThat(registration.getTaskSubscriptions()).isEmpty();
    // the worker class is still a bean
    assertThat(Arc.container().instance(SimpleWorker.class).isAvailable()).isTrue();
  }
}
