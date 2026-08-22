package dev.bpmcrafters.processengine.worker.quarkus.deployment;

import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.InMemoryTaskSubscriptionApi;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.RecordingServiceTaskCompletionApi;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.TestSupport;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.TransactionalWorker;
import io.quarkus.test.QuarkusExtensionTest;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario 6: 'complete-tasks-before-commit=true' applies to workers with default completion.
 */
class CompleteBeforeCommitConfigTest {

  @RegisterExtension
  static final QuarkusExtensionTest TEST = TestSupport.withoutJpa(new QuarkusExtensionTest()
    .setArchiveProducer(() -> TestSupport.archive(TransactionalWorker.class))
    .overrideConfigKey("dev.bpm-crafters.process-api.worker.complete-tasks-before-commit", "true"));

  @Inject
  InMemoryTaskSubscriptionApi subscriptionApi;

  @Inject
  RecordingServiceTaskCompletionApi completionApi;

  @Inject
  TransactionSynchronizationRegistry synchronizationRegistry;

  @Test
  void completesBeforeCommit() {
    List<Integer> statusAtCompletion = new CopyOnWriteArrayList<>();
    completionApi.setListener(() -> statusAtCompletion.add(synchronizationRegistry.getTransactionStatus()));

    subscriptionApi.deliver("tx-after-commit", "task-1", Map.of());

    assertThat(completionApi.getCompleted()).hasSize(1);
    assertThat(statusAtCompletion).containsExactly(Status.STATUS_ACTIVE);
  }
}
