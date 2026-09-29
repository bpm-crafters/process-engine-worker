package dev.bpmcrafters.processengine.worker.quarkus.deployment;

import dev.bpmcrafters.processengine.worker.quarkus.JtaTransactionalExecutor;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.InMemoryTaskSubscriptionApi;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.RecordingServiceTaskCompletionApi;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.TestSupport;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.TransactionalWorker;
import dev.bpmcrafters.processengine.worker.transaction.TransactionalExecutor;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario 6: transactional workers with quarkus-narayana-jta.
 */
class TransactionalWorkerTest {

  @RegisterExtension
  static final QuarkusExtensionTest TEST = TestSupport.withoutJpa(new QuarkusExtensionTest()
    .setArchiveProducer(() -> TestSupport.archive(TransactionalWorker.class)));

  @Inject
  InMemoryTaskSubscriptionApi subscriptionApi;

  @Inject
  RecordingServiceTaskCompletionApi completionApi;

  @Inject
  TransactionalWorker worker;

  @Inject
  TransactionSynchronizationRegistry synchronizationRegistry;

  private final List<Integer> statusAtCompletion = new CopyOnWriteArrayList<>();

  @BeforeEach
  void reset() {
    completionApi.reset();
    worker.reset();
    statusAtCompletion.clear();
    completionApi.setListener(() -> statusAtCompletion.add(synchronizationRegistry.getTransactionStatus()));
  }

  @Test
  void jtaExecutorIsActive() {
    TransactionalExecutor executor = Arc.container().instance(TransactionalExecutor.class).get();
    assertThat(ClientProxy.unwrap(executor)).isInstanceOf(JtaTransactionalExecutor.class);
  }

  @Test
  void completesAfterCommitByDefault() {
    subscriptionApi.deliver("tx-after-commit", "task-1", Map.of());

    assertThat(worker.getStatusAtInvocation()).containsExactly(Status.STATUS_ACTIVE);
    assertThat(worker.getCompletionStatus()).containsExactly(Status.STATUS_COMMITTED);
    assertThat(completionApi.getCompleted()).hasSize(1);
    assertThat(completionApi.getCompleted().get(0).get()).isEqualTo(Map.of("tx", "after"));
    assertThat(statusAtCompletion).containsExactly(Status.STATUS_NO_TRANSACTION);
  }

  @Test
  void completesBeforeCommitIfConfigured() {
    subscriptionApi.deliver("tx-before-commit", "task-2", Map.of());

    assertThat(worker.getStatusAtInvocation()).containsExactly(Status.STATUS_ACTIVE);
    assertThat(worker.getCompletionStatus()).containsExactly(Status.STATUS_COMMITTED);
    assertThat(completionApi.getCompleted()).hasSize(1);
    assertThat(statusAtCompletion).containsExactly(Status.STATUS_ACTIVE);
  }

  @Test
  void bpmnErrorRollsBackAndReportsError() {
    subscriptionApi.deliver("tx-bpmn-error", "task-3", Map.of());

    assertThat(worker.getCompletionStatus()).containsExactly(Status.STATUS_ROLLEDBACK);
    assertThat(completionApi.getCompletedByError()).hasSize(1);
    assertThat(completionApi.getCompletedByError().get(0).getErrorCode()).isEqualTo("TX_ERROR");
    assertThat(completionApi.getCompleted()).isEmpty();
    assertThat(completionApi.getFailed()).isEmpty();
    assertThat(statusAtCompletion).containsExactly(Status.STATUS_NO_TRANSACTION);
  }

  @Test
  void runtimeExceptionRollsBackAndFailsTask() {
    subscriptionApi.deliver("tx-runtime-error", "task-4", Map.of());

    assertThat(worker.getCompletionStatus()).containsExactly(Status.STATUS_ROLLEDBACK);
    assertThat(completionApi.getFailed()).hasSize(1);
    assertThat(completionApi.getFailed().get(0).getReason()).isEqualTo("runtime error in tx");
    assertThat(completionApi.getCompleted()).isEmpty();
  }
}
