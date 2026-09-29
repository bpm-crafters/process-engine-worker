package dev.bpmcrafters.processengine.worker.quarkus.deployment;

import dev.bpmcrafters.processengine.worker.idempotency.EntityManagerJpaIdempotencyRegistry;
import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry;
import dev.bpmcrafters.processengine.worker.idempotency.TaskLogEntry;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.InMemoryTaskSubscriptionApi;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.RecordingServiceTaskCompletionApi;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.TestSupport;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.IdempotentWorker;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.QuarkusExtensionTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3b: JPA idempotency registry (process-engine-worker-idempotency-registry-jpa + quarkus-hibernate-orm), results kept after completion.
 */
class JpaIdempotencyRegistryTest {

  @RegisterExtension
  static final QuarkusExtensionTest TEST = TestSupport.withJpa(new QuarkusExtensionTest()
    .setArchiveProducer(() -> TestSupport.archive(IdempotentWorker.class))
    .overrideConfigKey("dev.bpm-crafters.process-api.worker.remove-task-result-on-completion", "false"), "idempotency");

  @Inject
  InMemoryTaskSubscriptionApi subscriptionApi;

  @Inject
  RecordingServiceTaskCompletionApi completionApi;

  @Inject
  IdempotentWorker worker;

  @Inject
  EntityManager entityManager;

  @BeforeEach
  void reset() {
    completionApi.reset();
  }

  @Test
  void jpaRegistryIsActive() {
    assertThat(Arc.container().listAll(IdempotencyRegistry.class)).hasSize(1);
    assertThat(ClientProxy.unwrap(Arc.container().instance(IdempotencyRegistry.class).get())).isInstanceOf(EntityManagerJpaIdempotencyRegistry.class);
  }

  @Test
  void transactionalWorkerPersistsResultAndIsNotInvokedTwice() {
    subscriptionApi.deliver("idempotent-tx", "task-1", Map.of());

    TaskLogEntry entry = find("task-1");
    assertThat(entry).isNotNull();
    assertThat(entry.getProcessInstanceId()).isEqualTo("instance-task-1");
    assertThat(entry.getResult()).isEqualTo(Map.of("result", "tx-task-1"));

    subscriptionApi.deliver("idempotent-tx", "task-1", Map.of());
    assertThat(worker.invocationsOf("task-1")).isEqualTo(1);
    assertThat(completionApi.getCompleted()).hasSize(2);
    assertThat(completionApi.getCompleted().get(1).get()).isEqualTo(Map.of("result", "tx-task-1"));
  }

  @Test
  void nonTransactionalWorkerPersistsResultInOwnTransaction() {
    subscriptionApi.deliver("idempotent-plain", "task-2", Map.of());

    assertThat(find("task-2")).isNotNull();
    assertThat(find("task-2").getResult()).isEqualTo(Map.of("result", "plain-task-2"));

    subscriptionApi.deliver("idempotent-plain", "task-2", Map.of());
    assertThat(worker.invocationsOf("task-2")).isEqualTo(1);
    assertThat(completionApi.getCompleted()).hasSize(2);
  }

  @Test
  void rollbackLeavesNoRowAndRedeliveryInvokesAgain() {
    subscriptionApi.deliver("idempotent-fail-first", "task-3", Map.of());
    assertThat(completionApi.getFailed()).hasSize(1);
    assertThat(find("task-3")).isNull();

    subscriptionApi.deliver("idempotent-fail-first", "task-3", Map.of());
    assertThat(worker.invocationsOf("task-3")).isEqualTo(2);
    assertThat(completionApi.getCompleted()).hasSize(1);
    assertThat(find("task-3")).isNotNull();
  }

  @Test
  void bpmnErrorIsReportedAndLeavesNoRow() {
    subscriptionApi.deliver("idempotent-bpmn-error", "task-4", Map.of());
    assertThat(completionApi.getCompletedByError()).hasSize(1);
    assertThat(completionApi.getCompletedByError().get(0).getErrorCode()).isEqualTo("IDEMPOTENT_ERROR");
    assertThat(find("task-4")).isNull();
  }

  private TaskLogEntry find(String taskId) {
    return QuarkusTransaction.requiringNew().call(() -> entityManager.find(TaskLogEntry.class, taskId));
  }
}
