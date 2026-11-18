package dev.bpmcrafters.processengine.worker.quarkus.deployment

import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionImple
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.quarkus.JtaInMemoryIdempotencyRegistry
import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.task.*
import io.quarkus.narayana.jta.runtime.TransactionConfiguration
import io.quarkus.test.QuarkusExtensionTest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.transaction.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class TransactionConfigurationTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot {
      it.addClasses(MethodWorkers::class.java, ClassWorker::class.java, Engine::class.java,
        Outcome::class.java, Handle::class.java, Metadata::class.java, RegistryProducer::class.java)
    }.overrideConfigKey("worker-test.transaction-timeout", "43")

    private fun task(id: String) = TaskInformation(id, mapOf("processInstanceId" to "process"))
  }

  @Inject
  lateinit var engine: Engine
  @Inject
  lateinit var transactions: TransactionManager
  @Inject
  lateinit var registry: IdempotencyRegistry

  @Test
  fun methodTimeoutRunsInCdiOwnedRequiredTransactionAndCompletesAfterCommit() {
    assertConfiguredDelivery("timeout-method-after", 37, beforeCommit = false)
  }

  @Test
  fun methodTimeoutPreservesBeforeCommitCompletion() {
    assertConfiguredDelivery("timeout-method-before", 37, beforeCommit = true)
  }

  @Test
  fun classTimeoutRunsInCdiOwnedRequiredTransaction() {
    assertConfiguredDelivery("timeout-class", 41, beforeCommit = false)
  }

  @Test
  fun propertyTimeoutOverridesAnnotationFallback() {
    assertConfiguredDelivery("timeout-property", 43, beforeCommit = false)
  }

  private fun assertConfiguredDelivery(topic: String, timeout: Int, beforeCommit: Boolean) {
    val task = task(topic)
    assertThat(transactions.transaction).isNull()
    engine.deliver(topic, task)
    assertThat(engine.calls[task.taskId]).isEqualTo(1)
    assertThat(engine.timeouts[task.taskId]).isEqualTo(timeout)
    assertThat(engine.outcomes[task.taskId]).isEqualTo(Status.STATUS_COMMITTED)
    assertThat(engine.completions[task.taskId]).isEqualTo(1)
    if (beforeCommit) {
      assertThat(engine.completing[task.taskId]).isSameAs(engine.business[task.taskId])
      assertThat(engine.outcomesAtCompletion).doesNotContainKey(task.taskId)
    } else {
      assertThat(engine.completing).containsKey(task.taskId)
      assertThat(engine.completing[task.taskId]).isNull()
      assertThat(engine.outcomesAtCompletion[task.taskId]).isEqualTo(Status.STATUS_COMMITTED)
    }
    assertThat(engine.failures).doesNotContainKey(task.taskId)
    assertThat(registry.getTaskResult(task)).isNull()
    assertThat(transactions.transaction).isNull()
  }

  @Test
  fun configuredRequiredWorkerReplaysCommittedCacheWithoutInvokingBusinessAgain() {
    val task = task("timeout-replay")
    engine.offline.add(task.taskId)
    engine.deliver("timeout-property", task)
    assertThat(engine.calls[task.taskId]).isEqualTo(1)
    assertThat(engine.outcomes[task.taskId]).isEqualTo(Status.STATUS_COMMITTED)
    assertThat(registry.getTaskResult(task)).containsEntry("value", task.taskId)
    assertThat(engine.failures[task.taskId]!!.reason).isEqualTo("completion offline")
    engine.offline.remove(task.taskId)
    engine.deliver("timeout-property", task)
    assertThat(engine.calls[task.taskId]).isEqualTo(1)
    assertThat(engine.completions[task.taskId]).isEqualTo(1)
    assertThat(registry.getTaskResult(task)).isNull()
  }

  @Test
  fun configuredRequiredBeforeCommitFailureRollsBackBusinessAndCache() {
    val task = task("timeout-before-failure")
    engine.offline.add(task.taskId)
    engine.deliver("timeout-method-before", task)
    assertThat(engine.calls[task.taskId]).isEqualTo(1)
    assertThat(engine.timeouts[task.taskId]).isEqualTo(37)
    assertThat(engine.outcomes[task.taskId]).isEqualTo(Status.STATUS_ROLLEDBACK)
    assertThat(engine.completing[task.taskId]).isSameAs(engine.business[task.taskId])
    assertThat(engine.failures[task.taskId]!!.reason).isEqualTo("completion offline")
    assertThat(registry.getTaskResult(task)).isNull()
    assertThat(transactions.transaction).isNull()
  }

  @Test
  fun cachedBeforeCommitCompletionFailureKeepsCacheForSuccessfulReplay() {
    val task = task("timeout-cached-before-failure")
    val cached = mapOf<String, Any?>("value" to "cached")
    registry.register(task, cached)
    engine.offline.add(task.taskId)
    engine.deliver("timeout-method-before", task)
    assertThat(engine.calls).doesNotContainKey(task.taskId)
    assertThat(engine.completing[task.taskId]).isNotNull()
    assertThat(registry.getTaskResult(task)).isEqualTo(cached)
    assertThat(engine.failures[task.taskId]!!.reason).isEqualTo("completion offline")
    assertThat(engine.reporting).containsKey(task.taskId)
    assertThat(engine.reporting[task.taskId]).isNull()
    assertThat(engine.completions).doesNotContainKey(task.taskId)
    assertThat(transactions.transaction).isNull()

    engine.offline.remove(task.taskId)
    engine.deliver("timeout-method-before", task)
    assertThat(engine.calls).doesNotContainKey(task.taskId)
    assertThat(engine.completions[task.taskId]).isEqualTo(1)
    assertThat(registry.getTaskResult(task)).isNull()
    assertThat(transactions.transaction).isNull()
  }

  @ApplicationScoped
  class MethodWorkers {
    @Inject
    lateinit var engine: Engine

    @Transactional
    @TransactionConfiguration(timeout = 37)
    @ProcessEngineWorker(topic = "timeout-method-after")
    fun after(task: TaskInformation): Map<String, Any?> = engine.write(task)

    @Transactional
    @TransactionConfiguration(timeout = 37)
    @ProcessEngineWorker(topic = "timeout-method-before", completion = ProcessEngineWorker.Completion.BEFORE_COMMIT)
    fun before(task: TaskInformation): Map<String, Any?> = engine.write(task)

    @Transactional
    @TransactionConfiguration(timeout = 47, timeoutFromConfigProperty = "worker-test.transaction-timeout")
    @ProcessEngineWorker(topic = "timeout-property")
    fun property(task: TaskInformation): Map<String, Any?> = engine.write(task)
  }

  @ApplicationScoped
  @Transactional
  @TransactionConfiguration(timeout = 41)
  class ClassWorker {
    @Inject
    private lateinit var engine: Engine

    @ProcessEngineWorker(topic = "timeout-class")
    fun work(task: TaskInformation): Map<String, Any?> = engine.write(task)
  }

  class Handle : TaskSubscription
  class Metadata : MetaInfo
  @Singleton
  class RegistryProducer {
    @Produces
    @Singleton
    fun registry(transactions: TransactionSynchronizationRegistry): IdempotencyRegistry = JtaInMemoryIdempotencyRegistry(transactions)
  }

  class Outcome(private val engine: Engine, private val id: String) : Synchronization {
    override fun beforeCompletion() = Unit
    override fun afterCompletion(status: Int) { engine.outcomes[id] = status }
  }

  @Singleton
  class Engine : TaskSubscriptionApi, ServiceTaskCompletionApi {
    @Inject
    lateinit var transactions: TransactionManager
    val commands = ConcurrentHashMap<String, SubscribeForTaskCmd>()
    val calls = ConcurrentHashMap<String, Int>()
    val timeouts = ConcurrentHashMap<String, Int>()
    val business = ConcurrentHashMap<String, Transaction>()
    val outcomes = ConcurrentHashMap<String, Int>()
    val completing: MutableMap<String, Transaction?> = Collections.synchronizedMap(HashMap())
    val reporting: MutableMap<String, Transaction?> = Collections.synchronizedMap(HashMap())
    val outcomesAtCompletion = ConcurrentHashMap<String, Int>()
    val completions = ConcurrentHashMap<String, Int>()
    val failures = ConcurrentHashMap<String, FailTaskCmd>()
    val offline: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun deliver(topic: String, task: TaskInformation) { commands[topic]!!.action.accept(task, emptyMap()) }

    fun write(task: TaskInformation): Map<String, Any?> {
      val transaction = checkNotNull(transactions.transaction)
      business[task.taskId] = transaction
      calls.merge(task.taskId, 1, Int::plus)
      timeouts[task.taskId] = (transaction as TransactionImple).timeout
      transaction.registerSynchronization(Outcome(this, task.taskId))
      return mapOf("value" to task.taskId)
    }

    override fun subscribeForTask(command: SubscribeForTaskCmd): CompletableFuture<TaskSubscription> {
      commands[requireNotNull(command.taskDescriptionKey)] = command
      return CompletableFuture.completedFuture(Handle())
    }
    override fun unsubscribe(command: UnsubscribeFromTaskCmd): CompletableFuture<Empty> = done()
    override fun getSupportedRestrictions(): Set<String> = emptySet()
    override fun meta(instance: MetaInfoAware): MetaInfo = Metadata()
    override fun completeTask(command: CompleteTaskCmd): CompletableFuture<Empty> {
      completing[command.taskId] = transactions.transaction
      outcomes[command.taskId]?.let { outcomesAtCompletion[command.taskId] = it }
      if (command.taskId in offline) return CompletableFuture.failedFuture(IllegalStateException("completion offline"))
      completions.merge(command.taskId, 1, Int::plus)
      return done()
    }
    override fun completeTaskByError(command: CompleteTaskByErrorCmd): CompletableFuture<Empty> = done()
    override fun failTask(command: FailTaskCmd): CompletableFuture<Empty> {
      reporting[command.taskId] = transactions.transaction
      failures[command.taskId] = command
      return done()
    }
    private fun done(): CompletableFuture<Empty> = CompletableFuture.completedFuture(Empty)
  }
}
