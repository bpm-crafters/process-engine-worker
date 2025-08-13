package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProcessEngineStarterReflectionTest {

  @Test
  fun `finds worker with payload return type`() {

    class Bean {
      @ProcessEngineWorker
      fun method(@Suppress("UNUSED_PARAMETER") payload: Map<String, Any>): Map<String, Any> {
        return mapOf()
      }
    }
    val bean = Bean()
    assertThat(bean.getAnnotatedWorkers()[0].hasPayloadReturnType()).isTrue()
  }

  @Test
  fun `finds worker with topic ba value`() {

    class Bean {
      @ProcessEngineWorker("foo")
      fun method(@Suppress("UNUSED_PARAMETER") payload: Map<String, Any>): Map<String, Any> {
        return mapOf()
      }
    }
    val bean = Bean()
    assertThat(bean.getAnnotatedWorkers()[0].getTopic()).isEqualTo("foo")
  }

}
