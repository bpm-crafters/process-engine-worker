package dev.bpmcrafters.processengine.worker.quarkus.deployment;

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.InMemoryTaskSubscriptionApi;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.RecordingServiceTaskCompletionApi;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.TestSupport;
import dev.bpmcrafters.processengine.worker.transaction.TransactionalExecutor;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario 6c/7: an application-provided transactional executor (here: NONE) replaces the JTA default; a transactional
 * worker then runs without transaction and a warning is logged.
 */
class CustomTransactionalExecutorTest {

  @RegisterExtension
  static final QuarkusExtensionTest TEST = TestSupport.withoutJpa(new QuarkusExtensionTest()
    .setArchiveProducer(() -> TestSupport.archive(NoneExecutorProducer.class, TransactionalWorkerWithoutTx.class))
    .setLogRecordPredicate(r -> r.getMessage() != null && r.getMessage().contains("PROCESS-ENGINE-WORKER-025"))
    .assertLogRecords(records -> assertThat(records).hasSize(1)));

  @ApplicationScoped
  public static class NoneExecutorProducer {
    @Produces
    @Singleton
    public TransactionalExecutor executor() {
      return TransactionalExecutor.NONE;
    }
  }

  public static class TransactionalWorkerWithoutTx {
    static final AtomicInteger INVOCATIONS = new AtomicInteger();

    @Transactional
    @ProcessEngineWorker(topic = "tx")
    public Map<String, Object> work() {
      INVOCATIONS.incrementAndGet();
      return Map.of("ok", true);
    }
  }

  @Inject
  InMemoryTaskSubscriptionApi subscriptionApi;

  @Inject
  RecordingServiceTaskCompletionApi completionApi;

  @Test
  void runsWithoutTransaction() {
    assertThat(Arc.container().listAll(TransactionalExecutor.class)).hasSize(1);
    TransactionalExecutor executor = Arc.container().instance(TransactionalExecutor.class).get();
    assertThat(ClientProxy.unwrap(executor)).isSameAs(TransactionalExecutor.NONE);

    subscriptionApi.deliver("tx", "task-1", Map.of());
    assertThat(TransactionalWorkerWithoutTx.INVOCATIONS.get()).isEqualTo(1);
    assertThat(completionApi.getCompleted()).hasSize(1);
  }
}
