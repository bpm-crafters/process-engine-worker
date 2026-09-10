package dev.bpmcrafters.processengine.worker.quarkus.deployment;

import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.InMemoryTaskSubscriptionApi;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.RecordingServiceTaskCompletionApi;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.TestSupport;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.AbstractBaseWorker;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.ConcreteWorker;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusExtensionTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Worker methods declared (or declared abstract) on an abstract base class are registered on the concrete bean.
 */
class InheritedWorkerTest {

  @RegisterExtension
  static final QuarkusExtensionTest TEST = TestSupport.withoutJpa(new QuarkusExtensionTest()
    .setArchiveProducer(() -> TestSupport.archive(AbstractBaseWorker.class, ConcreteWorker.class)));

  @Inject
  InMemoryTaskSubscriptionApi subscriptionApi;

  @Inject
  RecordingServiceTaskCompletionApi completionApi;

  @Inject
  ConcreteWorker worker;

  @Test
  void inheritedAndOverriddenWorkersAreRegisteredOnConcreteBean() {
    assertThat(Arc.container().instance(ConcreteWorker.class).isAvailable()).isTrue();
    assertThat(subscriptionApi.getSubscriptions()).hasSize(2);

    subscriptionApi.deliver("inherited", "task-1", Map.of());
    subscriptionApi.deliver("overridden", "task-2", Map.of());

    assertThat(worker.getInvocations()).containsExactly("inherited:task-1", "overridden:task-2");
    assertThat(completionApi.getCompleted()).hasSize(2);
    assertThat(completionApi.getCompleted().get(1).get()).isEqualTo(Map.of("from", "concrete"));
  }
}
