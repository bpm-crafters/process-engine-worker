package dev.bpmcrafters.example.quarkus

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.task.*
import jakarta.inject.Singleton
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Demo only: synchronous delivery, no engine, persistence, retries or task locking. */
@Singleton
class DemoEngineAdapter : TaskSubscriptionApi, ServiceTaskCompletionApi {
  private val subscriptions = ConcurrentHashMap<String, Subscription>()
  private val pending = ConcurrentHashMap<String, CompletableFuture<Map<String, Any?>>>()
  private val sequence = AtomicLong()

  override fun subscribeForTask(command: SubscribeForTaskCmd): CompletableFuture<TaskSubscription> {
    val subscription = Subscription(command)
    if (subscriptions.putIfAbsent(command.taskDescriptionKey!!, subscription) != null) {
      return CompletableFuture.failedFuture(IllegalStateException("Duplicate demo topic"))
    }
    return CompletableFuture.completedFuture(subscription)
  }

  override fun unsubscribe(command: UnsubscribeFromTaskCmd): CompletableFuture<Empty> {
    (command.subscription as? Subscription)?.let {
      subscriptions.remove(it.command.taskDescriptionKey, it)
    }
    return CompletableFuture.completedFuture(Empty)
  }

  fun deliver(topic: String, payload: Map<String, Any?>): Map<String, Any?> {
    val subscription = subscriptions[topic] ?: error("No registered worker for $topic")
    val taskId = "demo-${sequence.incrementAndGet()}"
    val result = CompletableFuture<Map<String, Any?>>()
    pending[taskId] = result
    try {
      subscription.command.action.accept(TaskInformation(taskId, emptyMap()), payload)
      // This adapter supports only workers that complete synchronously during delivery.
      check(result.isDone) { "Demo worker did not complete synchronously" }
      return result.join()
    } finally {
      pending.remove(taskId)
    }
  }

  override fun completeTask(command: CompleteTaskCmd): CompletableFuture<Empty> {
    pending.getValue(command.taskId).complete(java.util.Map.copyOf(command.get()))
    return CompletableFuture.completedFuture(Empty)
  }

  override fun failTask(command: FailTaskCmd): CompletableFuture<Empty> = failed(command.taskId, command.reason)

  override fun completeTaskByError(command: CompleteTaskByErrorCmd): CompletableFuture<Empty> =
    failed(command.taskId, "BPMN error: ${command.errorCode}")

  private fun failed(taskId: String, reason: String): CompletableFuture<Empty> {
    pending.getValue(taskId).completeExceptionally(IllegalStateException(reason))
    return CompletableFuture.completedFuture(Empty)
  }

  override fun getSupportedRestrictions(): Set<String> = emptySet()
  override fun meta(caller: MetaInfoAware): MetaInfo = DemoMetadata()

  private data class Subscription(val command: SubscribeForTaskCmd) : TaskSubscription
  private class DemoMetadata : MetaInfo
}
