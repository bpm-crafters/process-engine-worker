package dev.bpmcrafters.processengine.worker.quarkus.deployment;

import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.InMemoryTaskSubscriptionApi;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.TestSupport;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.MultiTenantWorker;
import dev.bpmcrafters.processengineapi.CommonRestrictions;
import io.quarkus.test.QuarkusExtensionTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The duplicate topic validation is tenant-aware: the same topic may be served for different tenants.
 */
class MultiTenantTopicTest {

  @RegisterExtension
  static final QuarkusExtensionTest TEST = TestSupport.withoutJpa(new QuarkusExtensionTest()
    .setArchiveProducer(() -> TestSupport.archive(MultiTenantWorker.class)));

  @Inject
  InMemoryTaskSubscriptionApi subscriptionApi;

  @Test
  void sameTopicForDifferentTenantsIsAccepted() {
    assertThat(subscriptionApi.getSubscriptions()).hasSize(2);
    assertThat(subscriptionApi.getSubscriptions())
      .extracting(s -> s.getRestrictions().get(CommonRestrictions.TENANT_ID))
      .containsExactlyInAnyOrder("tenant-a", "tenant-b");
  }
}
