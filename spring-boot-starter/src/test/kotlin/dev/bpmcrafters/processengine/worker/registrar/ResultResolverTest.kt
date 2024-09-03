package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ResultResolverTest {
  private val resultResolver: ResultResolver = ResultResolver.builder().build()

  @Test
  fun `detects method returning map`() {
    class Worker {
      @ProcessEngineWorker
      fun work(): Map<String, Any> {
        return mapOf("string" to "value", "string2" to 67)
      }
    }

    val worker = Worker()
    val method = worker.getAnnotatedWorkers().first()

    assertThat(resultResolver.payloadReturnType(method)).isTrue()
    assertThat(resultResolver.resolve(method, worker.work())).containsAllEntriesOf(mapOf("string" to "value", "string2" to 67))
  }

  @Test
  fun `detects method returning generic map`() {
    class Worker {
      @ProcessEngineWorker
      fun work(): Map<String, out Any> {
        return mapOf("string" to "value", "string2" to "other")
      }
    }

    val worker = Worker()
    val method = worker.getAnnotatedWorkers().first()

    assertThat(resultResolver.payloadReturnType(method)).isTrue()
    assertThat(resultResolver.resolve(method, worker.work())).containsAllEntriesOf(mapOf("string" to "value", "string2" to "other"))
  }

  @Test
  fun `detects method returning subclass of map`() {
    class MyDelegateMap(val map: Map<String, Any>) : Map<String, Any> by map

    class Worker {
      @ProcessEngineWorker
      fun work(): MyDelegateMap {
        return MyDelegateMap(mapOf("string" to "value", "string2" to "other"))
      }
    }

    val worker = Worker()
    val method = worker.getAnnotatedWorkers().first()

    assertThat(resultResolver.payloadReturnType(method)).isTrue()
    assertThat(resultResolver.resolve(method, worker.work())).containsAllEntriesOf(mapOf("string" to "value", "string2" to "other"))
  }
}
