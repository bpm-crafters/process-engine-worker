package dev.bpmcrafters.processengine.worker.quarkus.deployment;

import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.InMemoryTaskSubscriptionApi;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.RecordingServiceTaskCompletionApi;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.TestSupport;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.InvocationWorker;
import dev.bpmcrafters.processengine.worker.registrar.JacksonVariableConverter;
import dev.bpmcrafters.processengine.worker.registrar.VariableConverter;
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario 2: worker invocation with parameter resolution, result conversion and auto-completion.
 */
class WorkerInvocationTest {

  @RegisterExtension
  static final QuarkusExtensionTest TEST = TestSupport.withoutJpa(new QuarkusExtensionTest()
    .setArchiveProducer(() -> TestSupport.archive(InvocationWorker.class)));

  @Inject
  InMemoryTaskSubscriptionApi subscriptionApi;

  @Inject
  RecordingServiceTaskCompletionApi completionApi;

  @Inject
  InvocationWorker worker;

  @BeforeEach
  void reset() {
    completionApi.reset();
    worker.getInvocations().clear();
  }

  @Test
  void invokesWorkerWithResolvedParametersAndCompletesWithResult() {
    subscriptionApi.deliver("invoke", "task-1", Map.of(
      "orderId", "order-1",
      "order", Map.of("id", "order-1", "amount", 42),
      "other", "ignored"
    ));

    assertThat(worker.getInvocations()).hasSize(1);
    Map<String, Object> invocation = worker.getInvocations().get(0);
    assertThat(invocation.get("orderId")).isEqualTo("order-1");
    assertThat(invocation.get("order")).isEqualTo(new InvocationWorker.Order("order-1", 42));
    assertThat(invocation.get("missing")).isNull();
    assertThat(invocation.get("taskId")).isEqualTo("task-1");
    assertThat(invocation.get("payload")).isEqualTo(Map.of(
      "orderId", "order-1",
      "order", Map.of("id", "order-1", "amount", 42),
      "other", "ignored"
    ));
    assertThat(ClientProxy.unwrap((VariableConverter) invocation.get("converter"))).isInstanceOf(JacksonVariableConverter.class);
    assertThat((ServiceTaskCompletionApi) invocation.get("completionApi")).isSameAs(completionApi);

    assertThat(completionApi.getCompleted()).hasSize(1);
    assertThat(completionApi.getCompleted().get(0).getTaskId()).isEqualTo("task-1");
    assertThat(completionApi.getCompleted().get(0).get()).isEqualTo(Map.of("result", "ok-order-1"));
    assertThat(completionApi.getFailed()).isEmpty();
  }

  @Test
  void doesNotAutoCompleteIfDisabled() {
    subscriptionApi.deliver("manual", "task-2", Map.of());
    assertThat(worker.getInvocations()).hasSize(1);
    // completed manually by the worker, not by the registrar
    assertThat(completionApi.getCompleted()).hasSize(1);
    assertThat(completionApi.getCompleted().get(0).get()).isEqualTo(Map.of("manual", true));

    completionApi.reset();
    subscriptionApi.deliver("void-no-complete", "task-3", Map.of());
    assertThat(completionApi.getCompleted()).isEmpty();
    assertThat(completionApi.getFailed()).isEmpty();
  }

  @Test
  void failsTaskIfMandatoryVariableIsMissing() {
    subscriptionApi.deliver("invoke", "task-4", Map.of("order", Map.of("id", "x", "amount", 1)));
    assertThat(worker.getInvocations()).isEmpty();
    assertThat(completionApi.getFailed()).hasSize(1);
    assertThat(completionApi.getFailed().get(0).getTaskId()).isEqualTo("task-4");
  }
}
