package dev.bpmcrafters.processengine.worker.quarkus.deployment;

import dev.bpmcrafters.processengine.worker.quarkus.MicrometerProcessEngineWorkerMetrics;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.InMemoryTaskSubscriptionApi;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.TestSupport;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.SimpleWorker;
import dev.bpmcrafters.processengine.worker.registrar.ProcessEngineWorkerMetrics;
import dev.bpmcrafters.processengine.worker.registrar.metrics.ProcessEngineWorkerMetricsMicrometer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With quarkus-micrometer on the classpath the Micrometer-based metrics are active.
 */
class MetricsTest {

  @RegisterExtension
  static final QuarkusExtensionTest TEST = TestSupport.withoutJpa(new QuarkusExtensionTest()
    .setArchiveProducer(() -> TestSupport.archive(SimpleWorker.class, SimpleRegistryProducer.class)));

  /**
   * Without a concrete registry (e.g. Prometheus) the composite registry of Quarkus does not record anything.
   */
  @ApplicationScoped
  public static class SimpleRegistryProducer {
    @Produces
    @Singleton
    public SimpleMeterRegistry simpleMeterRegistry() {
      return new SimpleMeterRegistry();
    }
  }

  @Inject
  InMemoryTaskSubscriptionApi subscriptionApi;

  @Inject
  SimpleMeterRegistry meterRegistry;

  @Test
  void micrometerMetricsAreRecorded() {
    ProcessEngineWorkerMetrics metrics = Arc.container().instance(ProcessEngineWorkerMetrics.class).get();
    assertThat(ClientProxy.unwrap(metrics)).isInstanceOf(MicrometerProcessEngineWorkerMetrics.class);

    subscriptionApi.deliver("workerC", "task-1", Map.of());

    assertThat(meterRegistry.counter(ProcessEngineWorkerMetricsMicrometer.PREFIX + ".received", "topic", "workerC").count()).isEqualTo(1.0);
    assertThat(meterRegistry.counter(ProcessEngineWorkerMetricsMicrometer.PREFIX + ".completed", "topic", "workerC").count()).isEqualTo(1.0);
  }
}
