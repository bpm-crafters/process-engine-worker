package dev.bpmcrafters.processengine.worker.idempotency

import dev.bpmcrafters.processengineapi.task.TaskInformation
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * In-Memory local implementation of the registry.
 */
class InMemoryIdempotencyRegistry(
  private val enableIdempotencyRegistry: Boolean,
  private val comparator: Comparator<TaskInformation> = Comparator { ti1, ti2 ->
    ti1.taskId.compareTo(ti2.taskId)
  }
) : IdempotencyRegistry {

  companion object {
    private val NULL_SENTINEL = Any()
  }

  private val invocations = ConcurrentHashMap<TaskInformation, Any>()

  override fun register(
    taskInformation: TaskInformation,
    invocationResult: Any?
  ): CompletableFuture<Any?> {
    invocations[taskInformation] = invocationResult ?: NULL_SENTINEL
    return CompletableFuture.completedFuture(invocationResult)
  }

  override fun hasTaskInformation(taskInformation: TaskInformation): CompletableFuture<Boolean> {
    return CompletableFuture.completedFuture(
      enableIdempotencyRegistry
        && invocations.keys.any { ti -> comparator.compare(ti, taskInformation) == 0 }
    )
  }

  override fun getResult(taskInformation: TaskInformation): CompletableFuture<Any?> {
    val key = invocations.keys.single { ti -> comparator.compare(ti, taskInformation) == 0 }
    val value = invocations[key]
    return CompletableFuture.completedFuture(if (value === NULL_SENTINEL) null else value)
  }


}
