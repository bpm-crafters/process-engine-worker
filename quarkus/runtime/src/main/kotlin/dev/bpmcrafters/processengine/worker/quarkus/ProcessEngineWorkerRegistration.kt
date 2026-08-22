package dev.bpmcrafters.processengine.worker.quarkus

import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.registrar.ParameterResolver
import dev.bpmcrafters.processengine.worker.registrar.ProcessEngineWorkerMetrics
import dev.bpmcrafters.processengine.worker.registrar.ProcessEngineWorkerRegistrar
import dev.bpmcrafters.processengine.worker.registrar.ResultResolver
import dev.bpmcrafters.processengine.worker.registrar.VariableConverter
import dev.bpmcrafters.processengine.worker.registrar.getAnnotatedWorkers
import dev.bpmcrafters.processengine.worker.transaction.AfterCommitHookAware
import dev.bpmcrafters.processengine.worker.transaction.TransactionalExecutor
import dev.bpmcrafters.processengine.worker.transaction.TransactionalMethodDetector
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskSubscription
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import dev.bpmcrafters.processengineapi.task.UnsubscribeFromTaskCmd
import io.github.oshai.kotlinlogging.KotlinLogging
import io.quarkus.arc.Arc
import io.quarkus.arc.ClientProxy
import io.quarkus.runtime.ShutdownEvent
import jakarta.annotation.Priority
import jakarta.enterprise.event.Observes
import jakarta.enterprise.inject.Instance
import jakarta.inject.Singleton
import jakarta.interceptor.Interceptor
import java.util.concurrent.CopyOnWriteArrayList

private val logger = KotlinLogging.logger {}

/**
 * Registers the process engine workers detected at build time with the core registrar during runtime initialization
 * (before any `StartupEvent` observer runs) and unsubscribes them on shutdown.
 *
 * All collaborators are resolved lazily via [Instance], so the application starts even if the process engine adapter is
 * not present, as long as the worker registration is disabled.
 * @since 0.8.6
 */
@Singleton
open class ProcessEngineWorkerRegistration(
  private val config: ProcessEngineWorkerConfig,
  private val taskSubscriptionApi: Instance<TaskSubscriptionApi>,
  private val taskCompletionApi: Instance<ServiceTaskCompletionApi>,
  private val variableConverter: Instance<VariableConverter>,
  private val parameterResolver: Instance<ParameterResolver>,
  private val resultResolver: Instance<ResultResolver>,
  private val transactionalExecutor: Instance<TransactionalExecutor>,
  private val metrics: Instance<ProcessEngineWorkerMetrics>,
  private val idempotencyRegistry: Instance<IdempotencyRegistry>,
) {

  private val subscriptions: MutableList<TaskSubscription> = CopyOnWriteArrayList()

  /**
   * Subscriptions created by the registration.
   */
  open val taskSubscriptions: List<TaskSubscription>
    get() = subscriptions.toList()

  /**
   * Registers the workers of the given bean classes.
   * @param workerClasses bean classes declaring worker methods.
   */
  open fun register(workerClasses: List<Class<*>>) {
    if (!config.enabled()) {
      logger.debug { "PROCESS-ENGINE-WORKER-020: Registration of process engine workers is disabled, skipping ${workerClasses.size} worker classes." }
      return
    }
    if (workerClasses.isEmpty()) {
      logger.debug { "PROCESS-ENGINE-WORKER-020: No process engine workers detected." }
      return
    }
    val registrar = createRegistrar()
    warnIfTransactionalWorkersWithoutTransactionManagement(workerClasses)
    val container = Arc.container()
    workerClasses.forEach { workerClass ->
      val handle = container.instance(workerClass)
      check(handle.isAvailable) { "PROCESS-ENGINE-WORKER-022: Could not resolve bean of worker class ${workerClass.name}." }
      val created = registrar.registerWorkers(handle.get(), workerClass, workerClass.simpleName)
      subscriptions.addAll(created)
      logger.debug { "PROCESS-ENGINE-WORKER-021: Registered ${created.size} process engine workers of ${workerClass.name}." }
    }
    logger.info { "PROCESS-ENGINE-WORKER-021: Registered ${subscriptions.size} process engine workers of ${workerClasses.size} worker classes." }
  }

  private fun createRegistrar(): ProcessEngineWorkerRegistrar {
    val subscriptionApi = resolve(taskSubscriptionApi, "TaskSubscriptionApi")
    val completionApi = resolve(taskCompletionApi, "ServiceTaskCompletionApi")
    val executor = transactionalExecutor.get()
    val registry = idempotencyRegistry.get()
    // install the after-commit hook on the raw instance, the client proxy of an interface-typed producer does not expose it
    val rawRegistry = ClientProxy.unwrap(registry)
    if (rawRegistry is AfterCommitHookAware) {
      rawRegistry.afterCommitHook = executor
    }
    return ProcessEngineWorkerRegistrar(
      configuration = config.toConfiguration(),
      taskSubscriptionApi = subscriptionApi,
      taskCompletionApi = completionApi,
      variableConverter = variableConverter.get(),
      parameterResolver = parameterResolver.get(),
      resultResolver = resultResolver.get(),
      transactionalExecutor = executor,
      processEngineWorkerMetrics = metrics.get(),
      idempotencyRegistry = registry,
      transactionalMethodDetector = TransactionalMethodDetector.DEFAULT,
    )
  }

  private fun warnIfTransactionalWorkersWithoutTransactionManagement(workerClasses: List<Class<*>>) {
    if (ClientProxy.unwrap(transactionalExecutor.get()) !== TransactionalExecutor.NONE) {
      return
    }
    val transactionalWorkers = workerClasses
      .flatMap { it.getAnnotatedWorkers() }
      .filter { TransactionalMethodDetector.DEFAULT.isTransactional(it) }
    if (transactionalWorkers.isNotEmpty()) {
      logger.warn {
        "PROCESS-ENGINE-WORKER-025: Found ${transactionalWorkers.size} transactional process engine workers " +
          "(${transactionalWorkers.joinToString { "${it.declaringClass.simpleName}#${it.name}" }}), but no transaction management is available. " +
          "The workers are executed without a transaction. Add the 'quarkus-narayana-jta' extension (and do not replace the transactional executor) to enable transactional workers."
      }
    }
  }

  private fun <T : Any> resolve(instance: Instance<T>, name: String): T {
    check(instance.isResolvable) {
      if (instance.isAmbiguous) {
        "PROCESS-ENGINE-WORKER-023: Found multiple $name beans, but exactly one is required to register process engine workers."
      } else {
        "PROCESS-ENGINE-WORKER-023: Found no $name bean. Add a process engine adapter (e.g. the Camunda 8 Quarkus adapter) " +
          "and enable it, or disable the worker registration via '${dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerConfiguration.ENABLED_PROPERTY}=false'."
      }
    }
    return try {
      instance.get()
    } catch (e: RuntimeException) {
      throw IllegalStateException("PROCESS-ENGINE-WORKER-023: Could not resolve $name required to register process engine workers: ${e.message}", e)
    }
  }

  /**
   * Unsubscribes all registered workers on shutdown. Runs with an explicit priority before default-priority observers
   * of the process engine adapters, so the subscriptions are removed before the deliveries are closed.
   * @param event shutdown event.
   */
  open fun onStop(@Observes @Priority(Interceptor.Priority.APPLICATION + 100) event: ShutdownEvent) {
    if (subscriptions.isEmpty()) {
      return
    }
    if (!taskSubscriptionApi.isResolvable) {
      return
    }
    val api = taskSubscriptionApi.get()
    subscriptions.forEach { subscription ->
      try {
        api.unsubscribe(UnsubscribeFromTaskCmd(subscription)).get()
      } catch (e: Exception) {
        logger.warn(e) { "PROCESS-ENGINE-WORKER-024: Could not unsubscribe $subscription on shutdown." }
      }
    }
    logger.debug { "PROCESS-ENGINE-WORKER-024: Unsubscribed ${subscriptions.size} process engine workers." }
    subscriptions.clear()
  }
}
