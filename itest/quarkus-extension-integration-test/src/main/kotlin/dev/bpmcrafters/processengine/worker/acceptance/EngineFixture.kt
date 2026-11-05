package dev.bpmcrafters.processengine.worker.acceptance

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.deploy.DeployBundleCommand
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.deploy.DeploymentInformation
import dev.bpmcrafters.processengineapi.task.*
import io.quarkus.narayana.jta.QuarkusTransaction
import jakarta.inject.Singleton
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.time.Instant
import java.util.HexFormat
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Deterministic API adapter fixture; it does not emulate a process engine. */
@Singleton
class EngineFixture : TaskSubscriptionApi, ServiceTaskCompletionApi, DeploymentApi {
  private val subscriptions = ConcurrentHashMap<String, Subscription>()
  private val results = ConcurrentHashMap<String, Map<String, Any?>>()
  private val failures = ConcurrentHashMap<String, Map<String, Any?>>()
  private val errors = ConcurrentHashMap<String, Map<String, Any?>>()
  private val invocations = ConcurrentHashMap<String, AtomicInteger>()
  private val attempts = ConcurrentHashMap<String, AtomicInteger>()
  private val completionStatuses = ConcurrentHashMap<String, Int>()
  private val failing = ConcurrentHashMap.newKeySet<String>()
  private val deferred = ConcurrentHashMap.newKeySet<String>()
  private val gates = ConcurrentHashMap<String, CompletionGate>()
  private val endpointTransactions = ConcurrentHashMap<String, Any>()
  private val workerTransactions = ConcurrentHashMap<String, Any>()
  private val events = CopyOnWriteArrayList<String>()
  private val deployments = CopyOnWriteArrayList<Map<String, String>>()
  private val deploymentCount = AtomicInteger()

  override fun subscribeForTask(command: SubscribeForTaskCmd): CompletableFuture<TaskSubscription> {
    val subscription = Subscription(command)
    if (subscriptions.putIfAbsent(command.taskDescriptionKey!!, subscription) != null) {
      return CompletableFuture.failedFuture(IllegalStateException("Duplicate topic subscription"))
    }
    return CompletableFuture.completedFuture(subscription)
  }

  override fun unsubscribe(command: UnsubscribeFromTaskCmd): CompletableFuture<Empty> {
    (command.subscription as? Subscription)?.let { subscriptions.remove(it.command.taskDescriptionKey, it) }
    return CompletableFuture.completedFuture(Empty)
  }

  fun deliver(topic: String, task: TaskInformation, payload: Map<String, Any?>) {
    val subscription = subscriptions[topic] ?: throw IllegalArgumentException("No registered worker for $topic")
    events.add("${task.taskId}:delivery")
    val requested = subscription.command.payloadDescription
    val delivered = linkedMapOf<String, Any?>()
    payload.forEach { (name, value) ->
      if (requested == null || requested.contains(name)) delivered[name] = value
    }
    subscription.command.action.accept(task, delivered)
  }

  override fun completeTask(command: CompleteTaskCmd): CompletableFuture<Empty> {
    val id = command.taskId
    attempts.computeIfAbsent(id) { AtomicInteger() }.incrementAndGet()
    completionStatuses[id] = QuarkusTransaction.getStatus()
    events.add("$id:completion-attempt")
    if (failing.contains(id)) {
      return CompletableFuture.failedFuture(IllegalStateException("Fixture completion failure"))
    }
    if (deferred.contains(id)) {
      return gates.computeIfAbsent(id) { CompletionGate() }.register().thenApply { completed(command) }
    }
    return CompletableFuture.completedFuture(completed(command))
  }

  private fun completed(command: CompleteTaskCmd): Empty {
    results[command.taskId] = command.get()
    events.add("${command.taskId}:completed")
    return Empty
  }

  override fun failTask(command: FailTaskCmd): CompletableFuture<Empty> {
    failures[command.taskId] = linkedMapOf("reason" to command.reason, "errorDetails" to command.errorDetails,
      "retryCount" to command.retryCount, "backoff" to command.retryBackoff?.toString())
    events.add("${command.taskId}:failed")
    return CompletableFuture.completedFuture(Empty)
  }

  override fun completeTaskByError(command: CompleteTaskByErrorCmd): CompletableFuture<Empty> {
    errors[command.taskId] = mapOf("code" to command.errorCode)
    events.add("${command.taskId}:bpmn-error")
    return CompletableFuture.completedFuture(Empty)
  }

  override fun deploy(command: DeployBundleCommand): CompletableFuture<DeploymentInformation> {
    for (resource in command.resources) {
      try {
        resource.resourceStream.use { stream ->
          val bytes = stream.readAllBytes()
          deployments.add(mapOf("name" to resource.name, "content" to String(bytes, Charsets.UTF_8),
            "sha256" to HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))))
        }
      } catch (exception: IOException) {
        return CompletableFuture.failedFuture(exception)
      } catch (exception: NoSuchAlgorithmException) {
        return CompletableFuture.failedFuture(exception)
      }
    }
    val count = deploymentCount.incrementAndGet()
    return CompletableFuture.completedFuture(DeploymentInformation("fixture-$count", Instant.EPOCH, null))
  }

  fun invoked(id: String) {
    invocations.computeIfAbsent(id) { AtomicInteger() }.incrementAndGet()
    events.add("$id:business-invoked")
  }

  fun transactionEnded(id: String, status: Int) { events.add("$id:transaction-ended:$status") }

  fun endpointTransaction(id: String, key: Any?) {
    workerTransactions.remove(id)
    if (key == null) endpointTransactions.remove(id) else endpointTransactions[id] = key
  }

  fun workerTransaction(id: String, key: Any?) {
    workerTransactions[id] = requireNotNull(key) { "Business work requires an active transaction" }
  }

  fun workerReturned(id: String) { events.add("$id:worker-returned") }

  fun configureCompletion(id: String, mode: String) {
    failing.remove(id)
    deferred.remove(id)
    when (mode) {
      "fail" -> failing.add(id)
      "pending" -> deferred.add(id)
      "success" -> Unit // Immediate successful future.
      else -> throw IllegalArgumentException("Unknown completion mode: $mode")
    }
  }

  fun release(id: String, success: Boolean) { gates.computeIfAbsent(id) { CompletionGate() }.release(success) }

  fun taskState(id: String): Map<String, Any?> {
    val endpointKey = endpointTransactions[id]
    val workerKey = workerTransactions[id]
    return linkedMapOf("invocations" to count(invocations, id), "attempts" to count(attempts, id),
      "result" to results[id], "failure" to failures[id], "error" to errors[id],
      "completionStatus" to completionStatuses[id], "pending" to (gates[id]?.isPending() == true),
      "joinedAmbient" to endpointKey?.let { it == workerKey },
      "endpointTransaction" to endpointKey?.toString(), "workerTransaction" to workerKey?.toString(),
      "events" to events.filter { it.startsWith("$id:") })
  }

  fun overview(): Map<String, Any?> {
    val descriptions = java.util.TreeMap<String, Any?>()
    subscriptions.forEach { (topic, subscription) ->
      descriptions[topic] = linkedMapOf("variables" to subscription.command.payloadDescription,
        "restrictions" to subscription.command.restrictions, "taskType" to subscription.command.taskType.name)
    }
    return mapOf("topics" to subscriptions.keys.sorted(), "subscriptions" to descriptions,
      "deploymentCount" to deploymentCount.get(), "deployments" to java.util.List.copyOf(deployments))
  }

  private fun count(counts: Map<String, AtomicInteger>, id: String): Int = counts[id]?.get() ?: 0

  override fun getSupportedRestrictions(): Set<String> = emptySet()
  override fun meta(caller: MetaInfoAware): MetaInfo = FixtureMetadata()

  private data class Subscription(val command: SubscribeForTaskCmd) : TaskSubscription
  private class FixtureMetadata : MetaInfo
}
