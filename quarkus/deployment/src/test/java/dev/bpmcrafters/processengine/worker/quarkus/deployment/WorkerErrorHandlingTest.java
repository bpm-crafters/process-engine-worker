package dev.bpmcrafters.processengine.worker.quarkus.deployment;

import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.InMemoryTaskSubscriptionApi;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.RecordingServiceTaskCompletionApi;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.TestSupport;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.ErrorWorker;
import dev.bpmcrafters.processengineapi.task.CompleteTaskByErrorCmd;
import dev.bpmcrafters.processengineapi.task.FailTaskCmd;
import io.quarkus.test.QuarkusExtensionTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario 3: error reporting of workers.
 */
class WorkerErrorHandlingTest {

  @RegisterExtension
  static final QuarkusExtensionTest TEST = TestSupport.withoutJpa(new QuarkusExtensionTest()
    .setArchiveProducer(() -> TestSupport.archive(ErrorWorker.class)));

  @Inject
  InMemoryTaskSubscriptionApi subscriptionApi;

  @Inject
  RecordingServiceTaskCompletionApi completionApi;

  @BeforeEach
  void reset() {
    completionApi.reset();
  }

  @Test
  void bpmnErrorCompletesTaskByError() {
    subscriptionApi.deliver("bpmn-error", "task-1", Map.of());
    assertThat(completionApi.getCompletedByError()).hasSize(1);
    CompleteTaskByErrorCmd cmd = completionApi.getCompletedByError().get(0);
    assertThat(cmd.getTaskId()).isEqualTo("task-1");
    assertThat(cmd.getErrorCode()).isEqualTo("BUSINESS_ERROR");
    assertThat(cmd.getErrorMessage()).isEqualTo("business error happened");
    assertThat(cmd.get()).isEqualTo(Map.of("reason", "test"));
    assertThat(completionApi.getCompleted()).isEmpty();
    assertThat(completionApi.getFailed()).isEmpty();
  }

  @Test
  void runtimeExceptionFailsTaskWithDecrementedRetries() {
    subscriptionApi.deliver("runtime-error", "task-2", Map.of());
    assertThat(completionApi.getFailed()).hasSize(1);
    FailTaskCmd cmd = completionApi.getFailed().get(0);
    assertThat(cmd.getTaskId()).isEqualTo("task-2");
    assertThat(cmd.getReason()).isEqualTo("boom");
    assertThat(cmd.getRetryCount()).isEqualTo(2); // task information carries 3 retries
    assertThat(cmd.getRetryBackoff()).isNull();
    assertThat(cmd.getErrorDetails()).contains("IllegalStateException");
  }

  @Test
  void failJobExceptionFailsTaskWithGivenRetries() {
    subscriptionApi.deliver("fail-job", "task-3", Map.of());
    assertThat(completionApi.getFailed()).hasSize(1);
    FailTaskCmd cmd = completionApi.getFailed().get(0);
    assertThat(cmd.getReason()).isEqualTo("fail with retries");
    assertThat(cmd.getRetryCount()).isEqualTo(7);
    assertThat(cmd.getRetryBackoff()).isEqualTo(Duration.ofSeconds(30));
  }
}
