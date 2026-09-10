package dev.bpmcrafters.processengine.worker.quarkus.deployment;

import dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.invalid.PackagePrivateMethodWorker;
import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.fail;

/**
 * Scenario 5: non-public worker methods are rejected at build time (the registrar discovers public methods only).
 */
class PackagePrivateMethodValidationTest {

  @RegisterExtension
  static final QuarkusExtensionTest TEST = ValidationTestSupport.expectValidationError("must be public", PackagePrivateMethodWorker.class);

  @Test
  void buildFails() {
    fail("The build should fail.");
  }
}
