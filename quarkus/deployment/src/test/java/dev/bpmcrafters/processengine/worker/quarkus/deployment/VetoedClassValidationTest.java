package dev.bpmcrafters.processengine.worker.quarkus.deployment;

import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.invalid.VetoedWorker;
import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.fail;

/**
 * Scenario 5: build-time validation.
 */
class VetoedClassValidationTest {

  @RegisterExtension
  static final QuarkusExtensionTest TEST = ValidationTestSupport.expectValidationError("is not a bean", VetoedWorker.class);

  @Test
  void buildFails() {
    fail("The build should fail.");
  }
}
