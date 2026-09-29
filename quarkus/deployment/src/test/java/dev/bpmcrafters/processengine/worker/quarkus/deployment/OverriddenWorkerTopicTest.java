package dev.bpmcrafters.processengine.worker.quarkus.deployment;

import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.InMemoryTaskSubscriptionApi;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.TestSupport;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.OverridingTopicWorker;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.OverridingTopicWorkerBase;
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle;
import io.quarkus.test.QuarkusExtensionTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An annotated override replaces the worker declared on the parent: only the child's topic is registered and validated.
 */
class OverriddenWorkerTopicTest {

  @RegisterExtension
  static final QuarkusExtensionTest TEST = TestSupport.withoutJpa(new QuarkusExtensionTest()
    .setArchiveProducer(() -> TestSupport.archive(OverridingTopicWorker.class, OverridingTopicWorkerBase.class)));

  @Inject
  InMemoryTaskSubscriptionApi subscriptionApi;

  @Test
  void onlyTheOverridingTopicIsRegistered() {
    assertThat(subscriptionApi.getSubscriptions())
      .extracting(TaskSubscriptionHandle::getTaskDescriptionKey)
      .containsExactly("child-topic");
  }
}
