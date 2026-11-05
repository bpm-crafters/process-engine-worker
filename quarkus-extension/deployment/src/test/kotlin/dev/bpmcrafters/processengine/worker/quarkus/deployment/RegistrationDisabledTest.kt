package dev.bpmcrafters.processengine.worker.quarkus.deployment

import io.quarkus.test.QuarkusExtensionTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class RegistrationDisabledTest {
  companion object {
    @JvmField @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot { it.addClass(DisabledWorkerTest.UnusedWorker::class.java) }
      .overrideRuntimeConfigKey("dev.bpm-crafters.process-api.worker.register-process-workers", "false")
  }
  @Test fun startsWithoutEngineClientsWhenRegistrationIsDisabled() {}
}
