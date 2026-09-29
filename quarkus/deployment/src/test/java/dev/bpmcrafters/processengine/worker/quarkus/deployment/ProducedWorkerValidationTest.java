package dev.bpmcrafters.processengine.worker.quarkus.deployment;

import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.invalid.ProducedWorker;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.invalid.ProducedWorkerProducer;
import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Fail.fail;

/**
 * Scenario 5: worker classes exposed via a CDI producer are rejected with a clear message.
 */
class ProducedWorkerValidationTest {

  @RegisterExtension
  static final QuarkusExtensionTest TEST = ValidationTestSupport.expectValidationError("is exposed via the producer", ProducedWorker.class, ProducedWorkerProducer.class);

  @Test
  void buildFails() {
    fail("The build should fail.");
  }
}
