package dev.bpmcrafters.processengine.worker.quarkus.deployment;

import dev.bpmcrafters.processengine.worker.quarkus.ProcessEngineWorkerRegistration;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.InMemoryTaskSubscriptionApi;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.TestSupport;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.SimpleWorker;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.StartupObserver;
import dev.bpmcrafters.processengineapi.CommonRestrictions;
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle;
import dev.bpmcrafters.processengineapi.task.TaskType;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusExtensionTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario 1 + 9: un-annotated worker classes become beans, subscriptions carry topic, payload description and
 * restrictions, and the registration happens before the startup event.
 */
class WorkerRegistrationTest {

  @RegisterExtension
  static final QuarkusExtensionTest TEST = TestSupport.withoutJpa(new QuarkusExtensionTest()
    .setArchiveProducer(() -> TestSupport.archive(SimpleWorker.class, StartupObserver.class))
    .overrideConfigKey("dev.bpm-crafters.process-api.worker.tenant-id", "default-tenant"));

  @Inject
  InMemoryTaskSubscriptionApi subscriptionApi;

  @Inject
  StartupObserver startupObserver;

  @Inject
  ProcessEngineWorkerRegistration registration;

  @Test
  void unannotatedWorkerClassIsABean() {
    assertThat(Arc.container().instance(SimpleWorker.class).isAvailable()).isTrue();
  }

  @Test
  void subscriptionsAreCreatedWithTopicPayloadAndRestrictions() {
    assertThat(subscriptionApi.getSubscriptions()).hasSize(4);
    assertThat(registration.getTaskSubscriptions()).hasSize(4);

    TaskSubscriptionHandle a = subscriptionApi.getSubscription("topic-a");
    assertThat(a.getTaskType()).isEqualTo(TaskType.EXTERNAL);
    assertThat(a.getPayloadDescription()).isEqualTo(Set.of("orderId", "amount"));
    assertThat(a.getRestrictions()).containsEntry(CommonRestrictions.WORKER_LOCK_DURATION_IN_MILLISECONDS, "5000");
    assertThat(a.getRestrictions()).containsEntry(CommonRestrictions.TENANT_ID, "tenant-a");

    TaskSubscriptionHandle b = subscriptionApi.getSubscription("topic-b");
    assertThat(b.getPayloadDescription()).isNull();
    assertThat(b.getRestrictions()).containsEntry(CommonRestrictions.TENANT_ID, "default-tenant");
    assertThat(b.getRestrictions()).doesNotContainKey(CommonRestrictions.WORKER_LOCK_DURATION_IN_MILLISECONDS);

    TaskSubscriptionHandle c = subscriptionApi.getSubscription("workerC");
    assertThat(c.getRestrictions()).containsEntry(CommonRestrictions.TENANT_ID, "default-tenant");

    // java shorthand @ProcessEngineWorker("topic-d") binds to 'value'
    assertThat(subscriptionApi.getSubscription("topic-d")).isNotNull();
  }

  @Test
  void workersAreRegisteredBeforeStartupEvent() {
    assertThat(startupObserver.getSubscriptionsAtStartup()).isEqualTo(4);
  }
}
