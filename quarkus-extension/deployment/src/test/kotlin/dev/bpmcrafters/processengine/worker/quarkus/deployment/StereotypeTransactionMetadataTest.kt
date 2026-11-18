package dev.bpmcrafters.processengine.worker.quarkus.deployment

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.quarkus.JtaInMemoryIdempotencyRegistry
import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.task.*
import io.quarkus.test.QuarkusExtensionTest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.enterprise.inject.Stereotype
import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.transaction.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.CompletableFuture

class StereotypeTransactionMetadataTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot {
      it.addClasses(
        Atomic::class.java, CommitFailures::class.java,
        PolicyWorker::class.java, Worker::class.java, Engine::class.java, RegistryProducer::class.java,
        Handle::class.java, Metadata::class.java, Outcome::class.java
      )
    }

    private fun task(id: String) = TaskInformation(id, mapOf("processInstanceId" to "process"))
  }

  @Inject
  lateinit var engine: Engine
  @Inject
  lateinit var registry: IdempotencyRegistry

  @Test
  fun stereotypeBeforeCommitCompletionFailureRollsBackBusinessAndCacheTogether() {
    val task = task("stereotype-failure")
    engine.deliver("stereotype-before", task)
    assertThat(engine.businessTransactions[task.taskId]).isNotNull().isSameAs(engine.completionTransactions[task.taskId])
    assertThat(engine.outcomes[task.taskId]).isEqualTo(Status.STATUS_ROLLEDBACK)
    assertThat(engine.failures).containsKey(task.taskId)
    assertThat(registry.getTaskResult(task)).isNull()
  }

  @Test
  fun stereotypeDefaultCompletionOccursAfterCommit() {
    val task = task("stereotype-after")
    engine.deliver("stereotype-after", task)
    assertThat(engine.businessTransactions[task.taskId]).isNotNull()
    assertThat(engine.completionTransactions[task.taskId]).isNull()
    assertThat(engine.outcomesAtCompletion[task.taskId]).isEqualTo(Status.STATUS_COMMITTED)
  }

  @Test
  fun methodTransactionOverridesStereotypeTransaction() {
    val task = task("stereotype-override")
    engine.deliver("stereotype-override", task)
    assertThat(engine.businessTransactions[task.taskId]).isNull()
    assertThat(engine.completionTransactions[task.taskId]).isNull()
    assertThat(engine.failures).doesNotContainKey(task.taskId)
  }

  @Test
  fun stereotypeRollbackPolicyIsNotReplacedByBlanketRollback() {
    val task = task("stereotype-policy")
    engine.deliver("stereotype-policy", task)
    assertThat(engine.outcomes[task.taskId]).isEqualTo(Status.STATUS_COMMITTED)
    assertThat(engine.failures[task.taskId]!!.reason).isEqualTo("allowed failure")
  }

  @Stereotype
  @Transactional
  @Retention(AnnotationRetention.RUNTIME)
  @Target(AnnotationTarget.CLASS)
  annotation class Atomic

  @Stereotype
  @Transactional(dontRollbackOn = [IllegalStateException::class])
  @Retention(AnnotationRetention.RUNTIME)
  @Target(AnnotationTarget.CLASS)
  annotation class CommitFailures

  @CommitFailures
  @ApplicationScoped
  class PolicyWorker {
    @Inject
    lateinit var engine: Engine

    @ProcessEngineWorker(topic = "stereotype-policy")
    @Throws(Exception::class)
    fun work(task: TaskInformation) {
      engine.business(task)
      throw IllegalStateException("allowed failure")
    }
  }

  @Atomic
  @ApplicationScoped
  class Worker {
    @Inject
    lateinit var engine: Engine

    @ProcessEngineWorker(topic = "stereotype-before", completion = ProcessEngineWorker.Completion.BEFORE_COMMIT)
    @Throws(Exception::class)
    fun before(task: TaskInformation): Map<String, Any?> = engine.business(task)

    @ProcessEngineWorker(topic = "stereotype-after")
    @Throws(Exception::class)
    fun after(task: TaskInformation): Map<String, Any?> = engine.business(task)

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    @ProcessEngineWorker(topic = "stereotype-override")
    @Throws(Exception::class)
    fun override(task: TaskInformation): Map<String, Any?> = engine.business(task)
  }

  class Handle : TaskSubscription
  class Metadata : MetaInfo
  data class Outcome(val engine: Engine, val taskId: String) : Synchronization {
    override fun beforeCompletion() {}
    override fun afterCompletion(status: Int) {
      engine.outcomes[taskId] = status
    }
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
    val commands = HashMap<String, SubscribeForTaskCmd>()
    val businessTransactions = HashMap<String, Transaction?>()
    val completionTransactions = HashMap<String, Transaction?>()
    val outcomes = HashMap<String, Int>()
    val outcomesAtCompletion = HashMap<String, Int?>()
    val failures = HashMap<String, FailTaskCmd>()
    fun deliver(topic: String, task: TaskInformation) {
      commands[topic]!!.action.accept(task, emptyMap())
    }

    @Throws(Exception::class)
    fun business(task: TaskInformation): Map<String, Any?> {
      val transaction = transactions.transaction
      businessTransactions[task.taskId] = transaction
      transaction?.registerSynchronization(Outcome(this, task.taskId))
      return mapOf("result" to true)
    }

    override fun subscribeForTask(command: SubscribeForTaskCmd): CompletableFuture<TaskSubscription> {
      commands[requireNotNull(command.taskDescriptionKey)] = command
      return CompletableFuture.completedFuture(Handle())
    }

    override fun unsubscribe(command: UnsubscribeFromTaskCmd): CompletableFuture<Empty> = CompletableFuture.completedFuture(Empty)
    override fun getSupportedRestrictions(): Set<String> = emptySet()
    override fun meta(instance: MetaInfoAware): MetaInfo = Metadata()
    override fun completeTask(command: CompleteTaskCmd): CompletableFuture<Empty> = try {
      completionTransactions[command.taskId] = transactions.transaction
      outcomesAtCompletion[command.taskId] = outcomes[command.taskId]
      if (command.taskId == "stereotype-failure") CompletableFuture.failedFuture(IllegalStateException("completion failed"))
      else CompletableFuture.completedFuture(Empty)
    } catch (failure: Exception) {
      CompletableFuture.failedFuture(failure)
    }

    override fun completeTaskByError(command: CompleteTaskByErrorCmd): CompletableFuture<Empty> = throw AssertionError("Unexpected business error")
    override fun failTask(command: FailTaskCmd): CompletableFuture<Empty> {
      failures[command.taskId] = command
      return CompletableFuture.completedFuture(Empty)
    }
  }
}
