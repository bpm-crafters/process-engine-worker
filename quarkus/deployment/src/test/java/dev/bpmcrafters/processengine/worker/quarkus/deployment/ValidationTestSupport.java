package dev.bpmcrafters.processengine.worker.quarkus.deployment;

import dev.bpmcrafters.processengine.worker.quarkus.ProcessEngineWorkerValidationException;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.TestSupport;
import io.quarkus.test.QuarkusExtensionTest;

import static org.assertj.core.api.Assertions.assertThat;

final class ValidationTestSupport {

  private ValidationTestSupport() {
  }

  static QuarkusExtensionTest expectValidationError(String messagePart, Class<?>... classes) {
    return TestSupport.withoutJpa(new QuarkusExtensionTest()
      .setArchiveProducer(() -> TestSupport.archive(classes))
      .assertException(t -> assertThat(TestSupport.anyInChain(t, e -> e instanceof ProcessEngineWorkerValidationException && e.getMessage().contains(messagePart)))
        .as("expected a ProcessEngineWorkerValidationException containing '" + messagePart + "' in " + t)
        .isTrue()));
  }
}
