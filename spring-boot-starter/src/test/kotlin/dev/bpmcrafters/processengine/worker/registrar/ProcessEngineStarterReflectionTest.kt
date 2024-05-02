package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProcessEngineStarterReflectionTest {

  @Test
  fun `finds worker with payload`() {

    class Bean {
      @ProcessEngineWorker
      fun method(@Suppress("UNUSED_PARAMETER") payload: Map<String, Any>) {}
    }
    val bean = Bean()
    assertThat(bean.getAnnotatedWorkers()[0].hasPayloadParameter(0)).isTrue()
  }
}
