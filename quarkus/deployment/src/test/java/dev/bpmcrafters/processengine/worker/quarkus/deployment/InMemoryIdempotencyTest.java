package dev.bpmcrafters.processengine.worker.quarkus.deployment;

import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry;
import dev.bpmcrafters.processengine.worker.idempotency.InMemoryIdempotencyRegistry;
import dev.bpmcrafters.processengine.worker.quarkus.JtaTransactionalExecutor;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.InMemoryTaskSubscriptionApi;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.RecordingServiceTaskCompletionApi;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.TestSupport;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.CustomBeans;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.IdempotentWorker;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario 8: in-memory idempotency registry provided by the application, with JTA after-commit hook.
 */
class InMemoryIdempotencyTest {

  @RegisterExtension
  static final QuarkusExtensionTest TEST = TestSupport.withoutJpa(new QuarkusExtensionTest()
    .setArchiveProducer(() -> TestSupport.archive(CustomBeans.class, IdempotentWorker.class))
    .overrideConfigKey("dev.bpm-crafters.process-api.worker.remove-task-result-on-completion", "false"));

  @Inject
  InMemoryTaskSubscriptionApi subscriptionApi;

  @Inject
  RecordingServiceTaskCompletionApi completionApi;

  @Inject
  IdempotentWorker worker;

  @BeforeEach
  void reset() {
    completionApi.reset();
  }

  @Test
  void afterCommitHookIsInstalled() {
    InMemoryIdempotencyRegistry registry = (InMemoryIdempotencyRegistry) ClientProxy.unwrap(Arc.container().instance(IdempotencyRegistry.class).get());
    assertThat(registry.getAfterCommitHook()).isInstanceOf(JtaTransactionalExecutor.class);
  }

  @Test
  void secondDeliveryDoesNotInvokeWorkerAgain() {
    subscriptionApi.deliver("idempotent-tx", "task-1", Map.of());
    subscriptionApi.deliver("idempotent-tx", "task-1", Map.of());

    assertThat(worker.invocationsOf("task-1")).isEqualTo(1);
    assertThat(completionApi.getCompleted()).hasSize(2);
    assertThat(completionApi.getCompleted().get(1).get()).isEqualTo(Map.of("result", "tx-task-1"));
  }

  @Test
  void rollbackDoesNotRegisterResult() {
    subscriptionApi.deliver("idempotent-fail-first", "task-2", Map.of());
    assertThat(completionApi.getFailed()).hasSize(1);
    assertThat(InMemoryTaskSubscriptionApi.taskInformation("task-2")).satisfies(info ->
      assertThat(Arc.container().instance(IdempotencyRegistry.class).get().getTaskResult(info)).isNull());

    subscriptionApi.deliver("idempotent-fail-first", "task-2", Map.of());
    assertThat(worker.invocationsOf("task-2")).isEqualTo(2);
    assertThat(completionApi.getCompleted()).hasSize(1);
  }
}
