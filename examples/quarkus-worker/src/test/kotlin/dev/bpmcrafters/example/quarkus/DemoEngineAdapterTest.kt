package dev.bpmcrafters.example.quarkus

import dev.bpmcrafters.processengineapi.task.*
import java.util.concurrent.CompletionException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class DemoEngineAdapterTest {
  @Test
  fun cleansUpFailedWorkerDeliveryAndAcceptsNextDelivery() {
    val adapter = DemoEngineAdapter()
    subscribe(adapter) { task, payload ->
      if (payload.containsKey("fail")) {
        adapter.failTask(FailTaskCmd(task.taskId, "Worker failed", null)).join()
      } else {
        adapter.completeTask(CompleteTaskCmd(task.taskId, payload)).join()
      }
    }
    assertThatThrownBy { adapter.deliver("test", mapOf("fail" to true)) }
      .isInstanceOf(CompletionException::class.java)
      .hasCauseInstanceOf(IllegalStateException::class.java)
      .hasRootCauseMessage("Worker failed")
    assertPendingEmpty(adapter)
    assertSuccessfulDelivery(adapter)
  }

  @Test
  fun cleansUpMissingSynchronousCompletionAndAcceptsNextDelivery() {
    val adapter = DemoEngineAdapter()
    subscribe(adapter) { task, payload ->
      if (!payload.containsKey("incomplete")) {
        adapter.completeTask(CompleteTaskCmd(task.taskId, payload)).join()
      }
    }
    assertThatThrownBy { adapter.deliver("test", mapOf("incomplete" to true)) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessage("Demo worker did not complete synchronously")
    assertPendingEmpty(adapter)
    assertSuccessfulDelivery(adapter)
  }

  @Test
  fun cleansUpThrownWorkerExceptionAndAcceptsNextDelivery() {
    val adapter = DemoEngineAdapter()
    subscribe(adapter) { task, payload ->
      if (payload.containsKey("throw")) throw IllegalArgumentException("Worker threw")
      adapter.completeTask(CompleteTaskCmd(task.taskId, payload)).join()
    }
    assertThatThrownBy { adapter.deliver("test", mapOf("throw" to true)) }
      .isInstanceOf(IllegalArgumentException::class.java).hasMessage("Worker threw")
    assertPendingEmpty(adapter)
    assertSuccessfulDelivery(adapter)
  }

  private fun subscribe(adapter: DemoEngineAdapter, handler: TaskHandler) {
    adapter.subscribeForTask(SubscribeForTaskCmd(emptyMap(), TaskType.EXTERNAL, "test", null,
      handler, TaskTerminationHandler { /* This synchronous demo never terminates tasks. */ })).join()
  }

  private fun assertSuccessfulDelivery(adapter: DemoEngineAdapter) {
    assertThat(adapter.deliver("test", mapOf("greeting" to "Hello Ada")))
      .containsExactlyEntriesOf(mapOf("greeting" to "Hello Ada"))
    assertPendingEmpty(adapter)
  }

  private fun assertPendingEmpty(adapter: DemoEngineAdapter) {
    // Inspect internal state without adding a production API solely for test observation.
    assertThat(adapter).extracting("pending").isEqualTo(emptyMap<String, Any?>())
  }
}
