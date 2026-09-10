package dev.bpmcrafters.processengine.worker.quarkus.deployment;

import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.invalid.AbstractMethodWorker;
import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.fail;

/**
 * Scenario 5: build-time validation.
 */
class AbstractMethodValidationTest {

  @RegisterExtension
  static final QuarkusExtensionTest TEST = ValidationTestSupport.expectValidationError("must not be abstract", AbstractMethodWorker.class, AbstractMethodWorker.Impl.class);

  @Test
  void buildFails() {
    fail("The build should fail.");
  }
}
