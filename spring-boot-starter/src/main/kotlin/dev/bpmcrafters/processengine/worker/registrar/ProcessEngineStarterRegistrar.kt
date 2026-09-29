package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker.Completion
import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerAutoConfiguration
import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerProperties
import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerProperties.Companion.DEFAULT_PREFIX
import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.transaction.AfterCommitHookAware
import dev.bpmcrafters.processengine.worker.transaction.SpringAfterCommitHook
import dev.bpmcrafters.processengine.worker.transaction.SpringTransactionalExecutor
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.aop.framework.Advised
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Lazy
import org.springframework.transaction.support.TransactionTemplate

private val logger = KotlinLogging.logger {}

/**
 * Registrar responsible for collecting process engine workers and creating corresponding external task subscriptions.
 * Acts as Spring [BeanPostProcessor] and delegates to the framework-independent [ProcessEngineWorkerRegistrar].
 * @since 0.0.3
 */
@AutoConfiguration(after = [ProcessEngineWorkerAutoConfiguration::class])
@ConditionalOnProperty(prefix = DEFAULT_PREFIX, name = ["enabled"], havingValue = "true", matchIfMissing = true)
class ProcessEngineStarterRegistrar(
  private val processEngineWorkerProperties: ProcessEngineWorkerProperties,
  @param:Lazy
  private val taskSubscriptionApi: TaskSubscriptionApi,
  @param:Lazy
  private val taskCompletionApi: ServiceTaskCompletionApi,
  @param:Lazy
  private val variableConverter: VariableConverter,
  @param:Lazy
  private val parameterResolver: ParameterResolver,
  @param:Lazy
  private val resultResolver: ResultResolver,
  @param:Lazy
  private val transactionalTemplate: TransactionTemplate,
  @param:Lazy
  private val processEngineWorkerMetrics: ProcessEngineWorkerMetrics,
  @param:Lazy
  private val idempotencyRegistry: IdempotencyRegistry
) : BeanPostProcessor {

  /*
   * The core registrar is created lazily, the collaborators are lazy proxies and must not be touched before the first worker is registered.
   */
  private val registrar: ProcessEngineWorkerRegistrar by lazy {
    // the registry is a lazy proxy, install the transaction synchronization on its target (covers registries not seen by this post processor)
    installAfterCommitHook(idempotencyRegistry)
    ProcessEngineWorkerRegistrar(
      configuration = processEngineWorkerProperties.toConfiguration(),
      taskSubscriptionApi = taskSubscriptionApi,
      taskCompletionApi = taskCompletionApi,
      variableConverter = variableConverter,
      parameterResolver = parameterResolver,
      resultResolver = resultResolver,
      transactionalExecutor = SpringTransactionalExecutor(transactionalTemplate),
      processEngineWorkerMetrics = processEngineWorkerMetrics,
      idempotencyRegistry = idempotencyRegistry
    )
  }

  override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
    // install transaction synchronization on components requiring it (e.g. in-memory idempotency registry).
    // this has to be done on the raw bean, since the injected registry is a lazy proxy.
    if (bean is AfterCommitHookAware) {
      logger.debug { "PROCESS-ENGINE-WORKER-003: Installing Spring transaction synchronization on $beanName." }
      bean.afterCommitHook = SpringAfterCommitHook
    }
    val targetClass = AopUtils.getTargetClass(bean)
    if (targetClass.getAnnotatedWorkers().isNotEmpty()) {
      registrar.registerWorkers(bean = bean, targetClass = targetClass, beanName = beanName)
    }
    return bean
  }

  /*
   * Unwraps Spring proxies (lazy resolution proxies, AOP proxies) and installs the after-commit hook on the target, if required.
   */
  private fun installAfterCommitHook(registry: IdempotencyRegistry) {
    var target: Any? = registry
    while (target is Advised) {
      target = target.targetSource.target
    }
    if (target is AfterCommitHookAware) {
      logger.debug { "PROCESS-ENGINE-WORKER-003: Installing Spring transaction synchronization on ${target.javaClass.simpleName}." }
      target.afterCommitHook = SpringAfterCommitHook
    }
  }

  /**
   * Calculates the retry information for a failed task.
   * @see ProcessEngineWorkerRegistrar.calculateRetry
   */
  internal fun calculateRetry(taskInformation: TaskInformation, cause: Throwable): ProcessEngineWorkerRegistrar.FailureRetry =
    registrar.calculateRetry(taskInformation, cause)

  /**
   * Determines if the task should be completed before the commit of the transaction.
   * @see ProcessEngineWorkerRegistrar.completeBeforeCommit
   */
  internal fun completeBeforeCommit(complete: Completion): Boolean = registrar.completeBeforeCommit(complete)

}
