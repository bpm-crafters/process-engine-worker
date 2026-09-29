package dev.bpmcrafters.processengine.worker.quarkus.deployment;

import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.invalid.PrivateMethodWorker;
import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.fail;

/**
 * Scenario 5: build-time validation.
 */
class PrivateMethodValidationTest {

  @RegisterExtension
  static final QuarkusExtensionTest TEST = ValidationTestSupport.expectValidationError("must be public", PrivateMethodWorker.class);

  @Test
  void buildFails() {
    fail("The build should fail.");
  }
}
