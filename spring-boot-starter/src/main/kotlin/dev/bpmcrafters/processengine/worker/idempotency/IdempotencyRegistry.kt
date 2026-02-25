package dev.bpmcrafters.processengine.worker.idempotency

import dev.bpmcrafters.processengineapi.task.TaskInformation
import java.util.concurrent.CompletableFuture

/**
 * Idempotency registry used to avoid duplicate worker invocations.
 * @since 0.8.0
 */
interface IdempotencyRegistry {

  fun register(taskInformation: TaskInformation, invocationResult: Any?): CompletableFuture<Any?>

  fun hasTaskInformation(taskInformation: TaskInformation): CompletableFuture<Boolean>

  fun getResult(taskInformation: TaskInformation): CompletableFuture<Any?>
}
