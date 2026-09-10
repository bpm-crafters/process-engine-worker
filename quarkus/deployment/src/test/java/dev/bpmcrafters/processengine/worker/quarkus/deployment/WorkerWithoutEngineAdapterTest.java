package dev.bpmcrafters.processengine.worker.quarkus.deployment;

import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.SimpleWorker;
import io.quarkus.test.QuarkusExtensionTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Without a TaskSubscriptionApi bean (no process engine adapter) the start fails with a clear message.
 */
class WorkerWithoutEngineAdapterTest {

  @RegisterExtension
  static final QuarkusExtensionTest TEST = new QuarkusExtensionTest()
    .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(SimpleWorker.class))
    .overrideConfigKey("quarkus.hibernate-orm.enabled", "false")
    .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
    .assertException(t -> assertThat(t)
      .hasMessageContaining("PROCESS-ENGINE-WORKER-023")
      .hasMessageContaining("TaskSubscriptionApi"));

  @Test
  void startFails() {
    fail("The application should not start.");
  }
}
