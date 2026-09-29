package dev.bpmcrafters.processengine.worker.idempotency

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerProperties
import dev.bpmcrafters.processengine.worker.registrar.ProcessEngineStarterRegistrar
import dev.bpmcrafters.processengine.worker.transaction.AfterCommitHookAware
import dev.bpmcrafters.processengine.worker.transaction.SpringAfterCommitHook
import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.aop.TargetSource
import org.springframework.aop.framework.ProxyFactory
import java.util.concurrent.CompletableFuture
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.util.*

/**
 * Verifies that the in-memory registry defers the registration until the Spring transaction has been committed.
 */
internal class InMemoryIdempotencyRegistrySpringTxTest {

  /**
   * Minimal resource-less transaction manager, sufficient for transaction synchronization.
   */
  private class NoopTransactionManager : AbstractPlatformTransactionManager() {
    override fun doGetTransaction(): Any = Any()
    override fun doBegin(transaction: Any, definition: TransactionDefinition) = Unit
    override fun doCommit(status: DefaultTransactionStatus) = Unit
    override fun doRollback(status: DefaultTransactionStatus) = Unit
  }

  private val transactionTemplate = TransactionTemplate(NoopTransactionManager())

  private fun task() = TaskInformation(UUID.randomUUID().toString(), mapOf(CommonRestrictions.PROCESS_INSTANCE_ID to UUID.randomUUID().toString()))

  @Test
  fun `registrar installs the spring after commit hook on the raw bean`() {
    val registry = InMemoryIdempotencyRegistry()
    val registrar = ProcessEngineStarterRegistrar(ProcessEngineWorkerProperties(), mock(), mock(), mock(), mock(), mock(), transactionTemplate, mock(), mock())
    registrar.postProcessAfterInitialization(registry, "registry")
    assertThat(registry.afterCommitHook).isSameAs(SpringAfterCommitHook)
  }

  @Test
  fun `registrar installs the spring after commit hook on the target of a lazy proxy`() {
    val registry = InMemoryIdempotencyRegistry()
    // emulates the JDK proxy created for a @Lazy injection point of the interface type
    val lazyProxy = ProxyFactory().apply {
      addInterface(IdempotencyRegistry::class.java)
      targetSource = object : TargetSource {
        override fun getTargetClass(): Class<*> = IdempotencyRegistry::class.java
        override fun getTarget(): Any = registry
      }
    }.proxy as IdempotencyRegistry
    assertThat(lazyProxy).isNotInstanceOf(AfterCommitHookAware::class.java)

    class Worker {
      @ProcessEngineWorker("topic")
      fun work() = Unit
    }

    val taskSubscriptionApi = mock<TaskSubscriptionApi> {
      on { subscribeForTask(any()) } doReturn CompletableFuture.completedFuture(mock())
    }
    val registrar = ProcessEngineStarterRegistrar(ProcessEngineWorkerProperties(), taskSubscriptionApi, mock(), mock(), mock(), mock(), transactionTemplate, mock(), lazyProxy)
    // the registry bean itself is not post processed (e.g. created before the post processor), a worker bean is
    registrar.postProcessAfterInitialization(Worker(), "worker")
    assertThat(registry.afterCommitHook).isSameAs(SpringAfterCommitHook)
  }

  @Test
  fun `registers after commit`() {
    val registry = InMemoryIdempotencyRegistry(SpringAfterCommitHook)
    val task = task()
    transactionTemplate.execute {
      registry.register(task, mapOf("a" to 1))
      assertThat(registry.getTaskResult(task)).isNull()
    }
    assertThat(registry.getTaskResult(task)).containsEntry("a", 1)
  }

  @Test
  fun `does not register on rollback`() {
    val registry = InMemoryIdempotencyRegistry(SpringAfterCommitHook)
    val task = task()
    transactionTemplate.execute { status ->
      registry.register(task, mapOf("a" to 1))
      status.setRollbackOnly()
    }
    assertThat(registry.getTaskResult(task)).isNull()
  }

  @Test
  fun `registers immediately without transaction`() {
    val registry = InMemoryIdempotencyRegistry(SpringAfterCommitHook)
    val task = task()
    registry.register(task, mapOf("a" to 1))
    assertThat(registry.getTaskResult(task)).containsEntry("a", 1)
  }
}
