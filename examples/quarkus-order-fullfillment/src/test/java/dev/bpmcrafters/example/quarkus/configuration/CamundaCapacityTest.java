package dev.bpmcrafters.example.quarkus.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bpmcrafters.processengineapi.adapter.c8.task.delivery.SubscribingRefreshingZeebeJobUserTaskDelivery;
import dev.bpmcrafters.processengineapi.adapter.c8.task.subscription.C8TaskSubscriptionApiImpl;
import dev.bpmcrafters.processengineapi.task.SubscribeForTaskCmd;
import dev.bpmcrafters.processengineapi.task.TaskTerminationHandler;
import dev.bpmcrafters.processengineapi.task.TaskType;
import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.client.impl.worker.JobWorkerBuilderImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Offline: construct the production client, but intercept open() before polling or streaming. */
class CamundaCapacityTest {
  private static final long COMPLETION_MARGIN_SECONDS = 60;

  private record WorkerSettings(String topic, int capacity, Duration lease) { }
  private record Settings(int threads, List<WorkerSettings> workers) { }

  @Test
  void serviceWorkerActivationCapacityMatchesFourExecutionSlots() throws Exception {
    final Settings settings = settings(60);
    assertThat(settings.threads()).isEqualTo(4);
    assertThat(settings.workers()).extracting(WorkerSettings::topic)
      .containsExactlyInAnyOrder("retrievePayment", "fetchGoods", "shipGoods");
    assertThat(settings.workers()).allSatisfy(worker ->
      assertThat(worker.capacity()).as("%s activation bound", worker.topic()).isEqualTo(settings.threads()));
  }

  @Test
  void nineInventoryJobsCannotBeLeasedBeyondTheirQueuedExecutionBudget() throws Exception {
    final Settings settings = settings(60);
    final WorkerSettings inventory = settings.workers().stream()
      .filter(worker -> worker.topic().equals("fetchGoods")).findFirst().orElseThrow();
    final int activatedInventoryJobs = Math.min(9, inventory.capacity());
    final long waves = ceilingDivide(activatedInventoryJobs, settings.threads());
    assertThat(inventory.lease().getSeconds()).as("nine orders: admitted inventory waves plus completion margin")
      .isGreaterThanOrEqualTo(waves * 60 + COMPLETION_MARGIN_SECONDS);
  }

  @ParameterizedTest
  @ValueSource(longs = {0, 1, 60, 180})
  void leasesCoverAggregateQueueAcrossAllThreeServiceWorkers(long delay) throws Exception {
    final Settings settings = settings(delay);
    final long aggregateCapacity = settings.workers().stream().mapToLong(WorkerSettings::capacity).sum();
    // All three topics share one SDK pool. Conservatively charge every admitted job a full inventory wait.
    final long waves = ceilingDivide(aggregateCapacity, settings.threads());
    final long requiredSeconds = Math.addExact(Math.multiplyExact(waves, delay), COMPLETION_MARGIN_SECONDS);
    assertThat(settings.workers()).allSatisfy(worker ->
      assertThat(worker.lease().getSeconds()).as("%s lease for %s shared-pool waves", worker.topic(), waves)
        .isGreaterThanOrEqualTo(requiredSeconds));
  }

  @ParameterizedTest
  @ValueSource(longs = {-1, Long.MAX_VALUE, Long.MAX_VALUE / 1000 + 1})
  void invalidOrOverflowingInventoryDelayIsRejectedBeforeWorkersOpen(long delay) {
    final var bridge = new CamundaBridge();
    final var client = mock(CamundaClient.class);
    assertThatThrownBy(() -> bridge.serviceDelivery(client, bridge.repository(), delay))
      .isInstanceOfAny(IllegalArgumentException.class, ArithmeticException.class);
    verifyNoInteractions(client);
  }

  private static long ceilingDivide(long jobs, int threads) { return (jobs + threads - 1) / threads; }

  private Settings settings(long delay) throws Exception {
    final var bridge = new CamundaBridge();
    try {
      final var configuration = bridge.client(new ObjectMapper(), "http://127.0.0.1:1", "http://127.0.0.1:2").getConfiguration();
      final var workers = new ArrayList<JobWorkerBuilderImpl>();
      final var client = capturingClient(configuration, workers);
      final var repository = bridge.repository();
      final var subscriptions = new C8TaskSubscriptionApiImpl(repository, mock(SubscribingRefreshingZeebeJobUserTaskDelivery.class));
      for (final String topic : List.of("retrievePayment", "fetchGoods", "shipGoods")) {
        subscriptions.subscribeForTask(new SubscribeForTaskCmd(Map.of(), TaskType.EXTERNAL, topic, null,
          (task, payload) -> { }, (TaskTerminationHandler) task -> { })).get();
      }
      bridge.serviceDelivery(client, repository, delay).subscribe();
      final List<WorkerSettings> captured = new ArrayList<>();
      for (final var worker : workers) captured.add(new WorkerSettings((String) field(worker, "jobType"),
        (Integer) field(worker, "maxJobsActive"), (Duration) field(worker, "timeout")));
      return new Settings(configuration.getNumJobWorkerExecutionThreads(), List.copyOf(captured));
    } finally {
      bridge.closeOwned();
    }
  }

  private CamundaClient capturingClient(CamundaClientConfiguration configuration, List<JobWorkerBuilderImpl> workers) {
    final var client = mock(CamundaClient.class);
    when(client.getConfiguration()).thenReturn(configuration);
    when(client.newWorker()).thenAnswer(call -> {
      final var builder = spy(new JobWorkerBuilderImpl(configuration, client, null, new ArrayList<>()));
      doReturn(mock(JobWorker.class)).when(builder).open();
      workers.add(builder);
      return builder;
    });
    return client;
  }

  private Object field(JobWorkerBuilderImpl builder, String name) throws Exception {
    // Pinned SDK 8.8.2 has no public getters for effective per-worker overrides.
    final var field = JobWorkerBuilderImpl.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(builder);
  }
}
