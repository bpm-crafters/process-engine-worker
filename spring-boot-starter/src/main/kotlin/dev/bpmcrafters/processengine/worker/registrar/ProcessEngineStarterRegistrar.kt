package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerAutoConfiguration
import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerProperties
import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerProperties.Companion.DEFAULT_PREFIX
import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import org.springframework.aop.support.AopUtils
import org.springframework.core.BridgeMethodResolver
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Lazy
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration

/** Spring discovery and transaction adapter for the framework-neutral worker runtime. */
@AutoConfiguration(after = [ProcessEngineWorkerAutoConfiguration::class])
@ConditionalOnProperty(prefix = DEFAULT_PREFIX, name = ["enabled"], havingValue = "true", matchIfMissing = true)
class ProcessEngineStarterRegistrar(
  private val processEngineWorkerProperties: ProcessEngineWorkerProperties,
  @param:Lazy private val taskSubscriptionApi: TaskSubscriptionApi,
  @param:Lazy private val taskCompletionApi: ServiceTaskCompletionApi,
  @param:Lazy private val variableConverter: VariableConverter,
  @param:Lazy private val parameterResolver: ParameterResolver,
  @param:Lazy private val resultResolver: ResultResolver,
  @param:Lazy private val transactionalTemplate: TransactionTemplate,
  @param:Lazy private val processEngineWorkerMetrics: ProcessEngineWorkerMetrics,
  @param:Lazy private val idempotencyRegistry: IdempotencyRegistry
) : BeanPostProcessor {

  @Deprecated("Use WorkerInvoker for programmatic worker registration")
  fun interface TaskHandlerWithResult : (TaskInformation, Map<String, Any?>) -> Any?

  @Deprecated("Retry calculation is handled by the core runtime")
  data class FailureRetry(val retryCount: Int?, val retryBackoff: Duration?)

  override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
    val targetClass = AopUtils.getTargetClass(bean)
    val methods = targetClass.methods.map { BridgeMethodResolver.findBridgedMethod(it) }.distinct().mapNotNull { method ->
      method.workerMetadata(targetClass)?.let { metadata -> method to metadata }
    }
    // Do not resolve lazy collaborators while processing infrastructure beans.
    if (methods.isEmpty()) return bean

    val runtime = ProcessEngineWorkerRegistrar(
      WorkerRuntimeOptions(
        processEngineWorkerProperties.completeTasksBeforeCommit,
        processEngineWorkerProperties.removeTaskResultOnCompletion,
        processEngineWorkerProperties.tenantId
      ),
      taskSubscriptionApi, taskCompletionApi, variableConverter, parameterResolver, resultResolver,
      { action -> requireNotNull(transactionalTemplate.execute { action.get() }) },
      processEngineWorkerMetrics, idempotencyRegistry
    )
    methods.forEach { (method, metadata) ->
      val proxyMethod = if (AopUtils.isJdkDynamicProxy(bean)) {
        targetClass.methods.firstOrNull { it.isBridge && BridgeMethodResolver.findBridgedMethod(it) == method } ?: method
      } else {
        method
      }
      val invocable = AopUtils.selectInvocableMethod(proxyMethod, bean.javaClass)
      // Match the method Spring intercepts, including interface metadata hidden by an inherited implementation.
      runtime.register(method, metadata, invocable.isTransactional(targetClass)) { arguments ->
        invocable.invoke(bean, *arguments)
      }
    }
    return bean
  }
}
