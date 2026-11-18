package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
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
  fun `finds worker with topic value`() {

    class Bean {
      @ProcessEngineWorker("foo")
      fun method(@Variable bar: String): Map<String, Any> {
        return mapOf()
      }
    }
    val bean = Bean()
    assertThat(bean.getAnnotatedWorkers()[0].getTopic()).isEqualTo("foo")
    val annotatedVariableParameters = bean.getAnnotatedWorkers()[0].parameters.filter { it.isVariable() }
    assertThat(annotatedVariableParameters).hasSize(1)
    assertThat(annotatedVariableParameters[0].extractVariableName()).isEqualTo("bar")
  }

  @Test
  fun `finds worker with variable name`() {

    class Bean {
      @ProcessEngineWorker("foo")
      fun method(@Variable("zee") bar: String): Map<String, Any> {
        return mapOf()
      }
    }
    val bean = Bean()
    assertThat(bean.getAnnotatedWorkers()[0].getTopic()).isEqualTo("foo")
    val annotatedVariableParameters = bean.getAnnotatedWorkers()[0].parameters.filter { it.isVariable() }
    assertThat(annotatedVariableParameters).hasSize(1)
    assertThat(annotatedVariableParameters[0].extractVariableName()).isEqualTo("zee")
  }

}
