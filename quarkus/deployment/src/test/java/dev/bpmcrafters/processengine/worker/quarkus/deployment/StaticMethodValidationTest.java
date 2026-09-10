package dev.bpmcrafters.processengine.worker.quarkus.deployment;

import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.invalid.StaticMethodWorker;
import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.fail;

/**
 * Scenario 5: build-time validation.
 */
class StaticMethodValidationTest {

  @RegisterExtension
  static final QuarkusExtensionTest TEST = ValidationTestSupport.expectValidationError("must not be static", StaticMethodWorker.class);

  @Test
  void buildFails() {
    fail("The build should fail.");
  }
}
