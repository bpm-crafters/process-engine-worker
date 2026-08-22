package dev.bpmcrafters.processengine.worker.quarkus.deployment;

import dev.bpmcrafters.processengine.worker.idempotency.TaskLogEntry;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.InMemoryTaskSubscriptionApi;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.RecordingServiceTaskCompletionApi;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.TestSupport;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.IdempotentWorker;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.QuarkusExtensionTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3b: with the default 'remove-task-result-on-completion=true' the result is removed after the task completion.
 */
class JpaIdempotencyRemoveOnCompletionTest {

  @RegisterExtension
  static final QuarkusExtensionTest TEST = TestSupport.withJpa(new QuarkusExtensionTest()
    .setArchiveProducer(() -> TestSupport.archive(IdempotentWorker.class)), "idempotency-remove");

  @Inject
  InMemoryTaskSubscriptionApi subscriptionApi;

  @Inject
  RecordingServiceTaskCompletionApi completionApi;

  @Inject
  IdempotentWorker worker;

  @Inject
  EntityManager entityManager;

  @Test
  void resultIsRemovedAfterCompletion() {
    subscriptionApi.deliver("idempotent-tx", "task-1", Map.of());
    subscriptionApi.deliver("idempotent-plain", "task-2", Map.of());

    assertThat(completionApi.getCompleted()).hasSize(2);
    assertThat(worker.getInvocations()).containsExactly("task-1", "task-2");
    List<TaskLogEntry> entries = QuarkusTransaction.requiringNew().call(() ->
      entityManager.createQuery("select e from TaskLogEntry e", TaskLogEntry.class).getResultList());
    assertThat(entries).isEmpty();
  }
}
