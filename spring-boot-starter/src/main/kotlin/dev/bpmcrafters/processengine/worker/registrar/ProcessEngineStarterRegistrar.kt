package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerAutoConfiguration
import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerProperties
import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerProperties.Companion.DEFAULT_PREFIX
import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengineapi.lifecycle.ProcessApplicationLifecycle
import dev.bpmcrafters.processengineapi.lifecycle.ProcessApplicationLifecycleContext
import dev.bpmcrafters.processengineapi.lifecycle.ProcessApplicationLifecycleParticipant
import dev.bpmcrafters.processengineapi.lifecycle.ProcessApplicationPhase
import dev.bpmcrafters.processengineapi.lifecycle.TaskSubscriptionRegistrationContext
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskInformation
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Lazy
import org.springframework.core.BridgeMethodResolver
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.util.ClassUtils
import java.lang.reflect.Method
import java.time.Duration

/**
 * Spring discovery and transaction adapter for the framework-neutral worker runtime.
 * @since 0.0.1
 **/
@AutoConfiguration(after = [ProcessEngineWorkerAutoConfiguration::class])
@ConditionalOnProperty(prefix = DEFAULT_PREFIX, name = ["enabled"], havingValue = "true", matchIfMissing = true)
class ProcessEngineStarterRegistrar(
  private val processEngineWorkerProperties: ProcessEngineWorkerProperties,
  @param:Lazy private val lifecycle: ProcessApplicationLifecycle,
  @param:Lazy private val taskCompletionApi: ServiceTaskCompletionApi,
  @param:Lazy private val variableConverter: VariableConverter,
  @param:Lazy private val parameterResolver: ParameterResolver,
  @param:Lazy private val resultResolver: ResultResolver,
  @param:Lazy private val transactionalTemplate: TransactionTemplate,
  @param:Lazy private val processEngineWorkerMetrics: ProcessEngineWorkerMetrics,
  @param:Lazy private val idempotencyRegistry: IdempotencyRegistry
) : BeanPostProcessor, SmartInitializingSingleton, ProcessApplicationLifecycleParticipant {

  private val pendingRegistrations = mutableListOf<(TaskSubscriptionApi) -> Unit>()
  private var activeSubscriptionApi: TaskSubscriptionApi? = null

  override fun afterSingletonsInstantiated() {
    lifecycle.register(this)
  }

  override fun getPhase(): Int = ProcessApplicationPhase.SUBSCRIPTION_REGISTRATION

  override fun onPhase(context: ProcessApplicationLifecycleContext) {
    val subscriptions = (context as TaskSubscriptionRegistrationContext).getTaskSubscriptionApi()
    while (true) {
      val registrations = synchronized(pendingRegistrations) {
        if (pendingRegistrations.isEmpty()) {
          activeSubscriptionApi = subscriptions
          return
        }
        pendingRegistrations.toList().also { pendingRegistrations.clear() }
      }
      registrations.forEach { it(subscriptions) }
    }
  }

  /**
   * Invokes a worker with task information and process variables and returns its result.
   * @since 0.0.1
   **/
  @Deprecated("Use WorkerInvoker for programmatic worker registration")
  fun interface TaskHandlerWithResult : (TaskInformation, Map<String, Any?>) -> Any?

  /**
   * Retry count and delay to send with a task failure.
   * @since 0.7.1
   **/
  @Deprecated("Retry calculation is handled by the core runtime")
  data class FailureRetry(val retryCount: Int?, val retryBackoff: Duration?)

  /**
   * Discovers annotated worker methods on the initialized Spring bean, preserving proxy interception.
   **/
  override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
    val targetClass = AopUtils.getTargetClass(bean)
    val methods = targetClass.methods.map { BridgeMethodResolver.findBridgedMethod(it) }.distinct().mapNotNull { method ->
      method.workerMetadata(targetClass)?.let { metadata -> method to metadata }
    }
    // Do not resolve lazy collaborators while processing infrastructure beans.
    if (methods.isEmpty()) return bean

    val register: (TaskSubscriptionApi) -> Unit = { subscriptions ->
      registerMethods(bean, targetClass, methods, subscriptions)
    }
    // Beans initialized after startup use the same API for dynamic subscriptions.
    val subscriptions = synchronized(pendingRegistrations) {
      activeSubscriptionApi ?: run {
        pendingRegistrations += register
        return bean
      }
    }
    register(subscriptions)
    return bean
  }

  private fun registerMethods(
    bean: Any,
    targetClass: Class<*>,
    methods: List<Pair<Method, Method>>,
    subscriptions: TaskSubscriptionApi
  ) {
    val runtime = ProcessEngineWorkerRegistrar(
      WorkerRuntimeOptions(
        completeTasksBeforeCommit = processEngineWorkerProperties.completeTasksBeforeCommit,
        removeTaskResultOnCompletion = processEngineWorkerProperties.removeTaskResultOnCompletion,
        tenantId = processEngineWorkerProperties.tenantId,
        backoffExceptions = processEngineWorkerProperties.backoffExceptions
      ),
      subscriptions, taskCompletionApi, variableConverter, parameterResolver, resultResolver,
      { action -> requireNotNull(transactionalTemplate.execute { action.get() }) },
      processEngineWorkerMetrics, idempotencyRegistry
    )
    val proxyClass = bean.javaClass
    methods.forEach { (method, metadata) ->
      val proxyMethod = if (AopUtils.isJdkDynamicProxy(bean) && !ClassUtils.hasMethod(proxyClass, method.name, *method.parameterTypes)) {
        targetClass.methods.firstOrNull { candidate ->
          candidate.isBridge && BridgeMethodResolver.findBridgedMethod(candidate) == method &&
            ClassUtils.hasMethod(proxyClass, candidate.name, *candidate.parameterTypes)
        } ?: method
      } else {
        method
      }
      val invocable = AopUtils.selectInvocableMethod(proxyMethod, proxyClass)
      // Match the method Spring intercepts, including interface metadata hidden by an inherited implementation.
      runtime.register(method, metadata, invocable.isTransactional(targetClass)) { arguments ->
        invocable.invoke(bean, *arguments)
      }
    }
  }
}
