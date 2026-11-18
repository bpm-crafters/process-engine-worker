package dev.bpmcrafters.processengine.worker.quarkus.deployment

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.quarkus.JtaInMemoryIdempotencyRegistry
import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.task.*
import io.quarkus.test.QuarkusExtensionTest
import io.quarkus.transaction.annotations.Rollback
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.transaction.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class QuarkusRollbackPolicyTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot {
      it.addClasses(Workers::class.java, Engine::class.java, RegistryProducer::class.java,
        CommitRuntimeFailure::class.java, InheritedCommitFailure::class.java, RollbackCheckedFailure::class.java,
        PlainCheckedWrapper::class.java, Outcome::class.java, Handle::class.java, Metadata::class.java)
    }

    private fun task(id: String) = TaskInformation(id, mapOf("processInstanceId" to "process"))
    private fun rollsBack(topic: String) = topic == "explicit-rollback" || topic == "checked-annotation"
  }

  @Inject
  lateinit var engine: Engine
  @Inject
  lateinit var transactions: TransactionManager
  @Inject
  lateinit var registry: IdempotencyRegistry

  @ParameterizedTest
  @ValueSource(strings = ["commit-annotation", "inherited-annotation", "explicit-rollback", "explicit-exemption", "checked-annotation", "checked-wrapper"])
  fun ownedRequiredUsesQuarkusRollbackPolicy(topic: String) {
    val task = task("owned-$topic")
    assertThat(transactions.transaction).isNull()
    engine.deliver(topic, task)
    assertThat(engine.calls[task.taskId]).isEqualTo(1)
    assertThat(engine.outcomes[task.taskId]).isEqualTo(if (rollsBack(topic)) Status.STATUS_ROLLEDBACK else Status.STATUS_COMMITTED)
    assertFailureOnly(task, topic)
    assertThat(transactions.transaction).isNull()
  }

  @ParameterizedTest
  @ValueSource(strings = ["commit-annotation", "inherited-annotation", "explicit-rollback", "explicit-exemption", "checked-annotation", "checked-wrapper"])
  fun ambientRequiredUsesQuarkusRollbackPolicyWithoutOwningCallerTransaction(topic: String) {
    val task = task("ambient-$topic")
    transactions.begin()
    try {
      val ambient = transactions.transaction
      engine.deliver(topic, task)
      assertThat(engine.calls[task.taskId]).isEqualTo(1)
      assertThat(engine.business[task.taskId]).isSameAs(ambient)
      assertThat(transactions.transaction).isSameAs(ambient)
      assertThat(ambient.status).isEqualTo(if (rollsBack(topic)) Status.STATUS_MARKED_ROLLBACK else Status.STATUS_ACTIVE)
      assertThat(engine.outcomes).doesNotContainKey(task.taskId)
      assertFailureOnly(task, topic)
      if (rollsBack(topic)) transactions.rollback() else transactions.commit()
      assertThat(engine.outcomes[task.taskId]).isEqualTo(if (rollsBack(topic)) Status.STATUS_ROLLEDBACK else Status.STATUS_COMMITTED)
      assertFailureOnly(task, topic)
    } finally {
      if (transactions.transaction != null) transactions.rollback()
    }
  }

  private fun assertFailureOnly(task: TaskInformation, topic: String) {
    assertThat(engine.failureAttempts[task.taskId]).isEqualTo(1)
    assertThat(engine.reporting).containsKey(task.taskId)
    assertThat(engine.reporting[task.taskId]).isNull()
    val reason = when (topic) {
      "commit-annotation", "inherited-annotation", "explicit-rollback" -> "commit annotated runtime"
      "checked-wrapper" -> "plain checked wrapper"
      else -> "rollback annotated checked"
    }
    assertThat(engine.failures[task.taskId]!!.reason).isEqualTo(reason)
    assertThat(engine.completions).doesNotContainKey(task.taskId)
    assertThat(engine.errors).doesNotContainKey(task.taskId)
    assertThat(registry.getTaskResult(task)).isNull()
  }

  @Rollback(false)
  open class CommitRuntimeFailure : RuntimeException("commit annotated runtime")
  class InheritedCommitFailure : CommitRuntimeFailure()
  @Rollback(true)
  class RollbackCheckedFailure : Exception("rollback annotated checked")
  // The checked wrapper, not its annotated cause, crosses the CDI boundary.
  class PlainCheckedWrapper(cause: Throwable) : Exception("plain checked wrapper", cause)

  @ApplicationScoped
  class Workers {
    @Inject
    lateinit var engine: Engine

    @Transactional
    @ProcessEngineWorker(topic = "commit-annotation")
    @Throws(Exception::class)
    fun commit(task: TaskInformation) {
      engine.write(task)
      throw CommitRuntimeFailure()
    }

    @Transactional
    @ProcessEngineWorker(topic = "inherited-annotation")
    @Throws(Exception::class)
    fun inherited(task: TaskInformation) {
      engine.write(task)
      throw InheritedCommitFailure()
    }

    @Transactional(rollbackOn = [CommitRuntimeFailure::class])
    @ProcessEngineWorker(topic = "explicit-rollback")
    @Throws(Exception::class)
    fun explicitRollback(task: TaskInformation) {
      engine.write(task)
      throw CommitRuntimeFailure()
    }

    @Transactional(rollbackOn = [Exception::class], dontRollbackOn = [RollbackCheckedFailure::class])
    @ProcessEngineWorker(topic = "explicit-exemption")
    @Throws(Exception::class)
    fun explicitExemption(task: TaskInformation) {
      engine.write(task)
      throw RollbackCheckedFailure()
    }

    @Transactional
    @ProcessEngineWorker(topic = "checked-annotation")
    @Throws(Exception::class)
    fun checked(task: TaskInformation) {
      engine.write(task)
      throw RollbackCheckedFailure()
    }

    @Transactional
    @ProcessEngineWorker(topic = "checked-wrapper")
    @Throws(Exception::class)
    fun wrapper(task: TaskInformation) {
      engine.write(task)
      throw PlainCheckedWrapper(RollbackCheckedFailure())
    }
  }

  class Handle : TaskSubscription
  class Metadata : MetaInfo
  class Outcome(private val engine: Engine, private val id: String) : Synchronization {
    override fun beforeCompletion() = Unit
    override fun afterCompletion(status: Int) { engine.outcomes[id] = status }
  }

  @Singleton
  class RegistryProducer {
    @Produces
    @Singleton
    fun registry(transactions: TransactionSynchronizationRegistry): IdempotencyRegistry = JtaInMemoryIdempotencyRegistry(transactions)
  }

  @Singleton
  class Engine : TaskSubscriptionApi, ServiceTaskCompletionApi {
    @Inject
    lateinit var transactions: TransactionManager
    val commands = ConcurrentHashMap<String, SubscribeForTaskCmd>()
    val calls = ConcurrentHashMap<String, Int>()
    val business = ConcurrentHashMap<String, Transaction>()
    val outcomes = ConcurrentHashMap<String, Int>()
    val reporting: MutableMap<String, Transaction?> = Collections.synchronizedMap(HashMap())
    val failureAttempts = ConcurrentHashMap<String, Int>()
    val failures = ConcurrentHashMap<String, FailTaskCmd>()
    val errors = ConcurrentHashMap<String, CompleteTaskByErrorCmd>()
    val completions = ConcurrentHashMap<String, Int>()

    fun deliver(topic: String, task: TaskInformation) { commands[topic]!!.action.accept(task, emptyMap()) }

    fun write(task: TaskInformation) {
      val transaction = checkNotNull(transactions.transaction)
      business[task.taskId] = transaction
      calls.merge(task.taskId, 1, Int::plus)
      transaction.registerSynchronization(Outcome(this, task.taskId))
    }

    override fun subscribeForTask(command: SubscribeForTaskCmd): CompletableFuture<TaskSubscription> {
      commands[requireNotNull(command.taskDescriptionKey)] = command
      return CompletableFuture.completedFuture(Handle())
    }
    override fun unsubscribe(command: UnsubscribeFromTaskCmd): CompletableFuture<Empty> = done()
    override fun getSupportedRestrictions(): Set<String> = emptySet()
    override fun meta(instance: MetaInfoAware): MetaInfo = Metadata()
    override fun completeTask(command: CompleteTaskCmd): CompletableFuture<Empty> {
      completions.merge(command.taskId, 1, Int::plus)
      return done()
    }
    override fun completeTaskByError(command: CompleteTaskByErrorCmd): CompletableFuture<Empty> {
      errors[command.taskId] = command
      return done()
    }
    override fun failTask(command: FailTaskCmd): CompletableFuture<Empty> {
      reporting[command.taskId] = transactions.transaction
      failureAttempts.merge(command.taskId, 1, Int::plus)
      failures[command.taskId] = command
      return done()
    }
    private fun done(): CompletableFuture<Empty> = CompletableFuture.completedFuture(Empty)
  }
}
