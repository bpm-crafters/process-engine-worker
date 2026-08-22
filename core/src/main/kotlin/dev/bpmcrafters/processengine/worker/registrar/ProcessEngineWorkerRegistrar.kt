package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred
import dev.bpmcrafters.processengine.worker.FailJobException
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker.Completion
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker.Completion.BEFORE_COMMIT
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker.Completion.DEFAULT
import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerConfiguration
import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.transaction.AfterCommitHookAware
import dev.bpmcrafters.processengine.worker.transaction.TransactionalExecutor
import dev.bpmcrafters.processengine.worker.transaction.TransactionalMethodDetector
import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.task.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.lang.reflect.Method
import java.time.Duration
import java.util.concurrent.ExecutionException

private val logger = KotlinLogging.logger {}

/**
 * Framework-independent registrar responsible for collecting process engine workers of a bean and creating
 * corresponding external task subscriptions. Framework integrations (Spring Boot starter, Quarkus extension)
 * discover the beans and delegate to this class.
 *
 * @param configuration worker configuration.
 * @param taskSubscriptionApi API to subscribe for tasks.
 * @param taskCompletionApi API to complete tasks.
 * @param variableConverter default variable converter.
 * @param parameterResolver resolver for the worker method parameters.
 * @param resultResolver resolver for the worker method results.
 * @param transactionalExecutor transaction management used for transactional workers.
 * @param processEngineWorkerMetrics metrics.
 * @param idempotencyRegistry registry to avoid duplicate worker invocations.
 * @param transactionalMethodDetector detector for transactional worker methods.
 * @since 0.8.6
 */
open class ProcessEngineWorkerRegistrar(
  private val configuration: ProcessEngineWorkerConfiguration,
  private val taskSubscriptionApi: TaskSubscriptionApi,
  private val taskCompletionApi: ServiceTaskCompletionApi,
  private val variableConverter: VariableConverter,
  private val parameterResolver: ParameterResolver,
  private val resultResolver: ResultResolver,
  private val transactionalExecutor: TransactionalExecutor,
  private val processEngineWorkerMetrics: ProcessEngineWorkerMetrics,
  private val idempotencyRegistry: IdempotencyRegistry,
  private val transactionalMethodDetector: TransactionalMethodDetector = TransactionalMethodDetector.DEFAULT,
) {

  private val exceptionResolver = ExceptionResolver()

  init {
    // an idempotency registry requiring transaction synchronization gets the hook of the transactional executor.
    if (idempotencyRegistry is AfterCommitHookAware) {
      idempotencyRegistry.afterCommitHook = transactionalExecutor
    }
  }

  /**
   * Registers all annotated worker methods of the bean as task subscriptions.
   *
   * @param bean bean instance to invoke the worker methods on. May be a proxy (transactional interception applies).
   * @param targetClass class to detect the worker methods on, defaults to the class of the bean. Frameworks using proxies
   * should pass the user class here.
   * @param beanName name of the bean used for logging.
   * @return created task subscriptions.
   */
  @JvmOverloads
  open fun registerWorkers(bean: Any, targetClass: Class<*> = bean.javaClass, beanName: String = targetClass.simpleName): List<TaskSubscription> {
    val annotatedProcessEngineWorkers = targetClass.getAnnotatedWorkers()

    if (annotatedProcessEngineWorkers.isNotEmpty()) {
      logger.debug { "PROCESS-ENGINE-WORKER-001: Detected ${annotatedProcessEngineWorkers.size} annotated workers on $beanName." }
      logger.trace { "PROCESS-ENGINE-WORKER-001: Detected annotated workers on $beanName are: ${annotatedProcessEngineWorkers.map { it.name }}." }
    }
    return annotatedProcessEngineWorkers.map { method ->
      registerWorker(bean, beanName, method)
    }
  }

  /**
   * Registers a single worker method.
   * @param bean bean instance to invoke the worker method on.
   * @param beanName name of the bean used for logging.
   * @param method worker method.
   * @return created task subscription.
   */
  open fun registerWorker(bean: Any, beanName: String, method: Method): TaskSubscription {
    val topic = method.getTopic()
    // detects among all result resolver if the specified payload may be converted to payload return type
    val payloadReturnType = resultResolver.payloadReturnType(method)
    val autoCompleteTask = method.getAutoComplete()

    // report misconfiguration because: the user selected to autocomplete, there is no result converter and the method has a non-void result.
    // so probably this result is not converted as payload - and either there should be no result
    // or there should be a matching converter strategy inside the result resolver
    if (autoCompleteTask && !method.hasVoidReturnType() && !payloadReturnType) {
      logger.warn { "PROCESS-ENGINE-WORKER-002: Found an unambiguous process task worker defined in $beanName#${method.name} having non-void and not payload compatible return type and auto-complete set to true." }
    }

    val annotatedVariableParameters = method.parameters.filter { it.isVariable() }

    val variableNames = if (annotatedVariableParameters.isNotEmpty()) {
      annotatedVariableParameters.extractVariableNames() // explicit variable names
    } else {
      null // null means no limitation
    }

    val completion = method.getCompletion()
    val customLockDuration = method.getLockDuration()
    val tenantId = method.getTenantId() ?: configuration.tenantId

    val restrictions: Map<String, String> = mutableMapOf<String, String>().apply {
      if (customLockDuration != null) {
        this[CommonRestrictions.WORKER_LOCK_DURATION_IN_MILLISECONDS] = customLockDuration.toString()
      }
      if (tenantId != null) {
        this[CommonRestrictions.TENANT_ID] = tenantId
      }
    }.toMap()

    // check if the method or class is marked to run in transaction
    val isTransactional = transactionalMethodDetector.isTransactional(method)

    val subscription = taskSubscriptionApi.subscribeForTask(
      subscribe(
        topic = topic,
        payloadDescription = variableNames,
        restrictions = restrictions,
        autoCompleteTask = autoCompleteTask,
        completion = completion,
        isTransactional = isTransactional,
        payloadReturnType = payloadReturnType,
        method = method,
      ) { taskInformation, payload ->
        val args: Array<Any?> = parameterResolver.createInvocationArguments(
          method = method,
          taskInformation = taskInformation,
          payload = payload,
          variableConverter = variableConverter,
          taskCompletionApi = taskCompletionApi
        )
        method.invoke(bean, *args) // spread the array, invoke on the (possibly proxied) bean to apply interceptors
      }
    )
    return subscription.get()
  }

  /**
   * Executes the subscription.
   * @param topic subscription topic.
   * @param payloadDescription description of the variables to be passed.
   * @param restrictions map of restrictions like customLockDuration for the worker
   * @param autoCompleteTask flag indicating if the task should be completed after execution of the worker.
   * @param isTransactional flag indicating if the task worker and task completion should run in a transaction.
   * @param payloadReturnType flag indicating of the return type of the method can be converted int payload.
   * @param method process engine worker method.
   * @param actionWithResult worker always returning the result.
   */
  private fun subscribe(
    topic: String,
    payloadDescription: Set<String>? = emptySet(),
    restrictions: Map<String, String> = mapOf(),
    autoCompleteTask: Boolean,
    completion: Completion,
    isTransactional: Boolean,
    payloadReturnType: Boolean,
    method: Method,
    actionWithResult: TaskHandlerWithResult
  ): SubscribeForTaskCmd = SubscribeForTaskCmd(
    restrictions = restrictions,
    taskType = TaskType.EXTERNAL,
    taskDescriptionKey = topic,
    payloadDescription = payloadDescription,
    action = { taskInformation, payload ->
      try {
        processEngineWorkerMetrics.taskReceived(topic)
        // depending on transactional annotations, execute either in a new transaction or direct
        if (isTransactional) {
          val completeBeforeCommit = completeBeforeCommit(completion)
          val txResult = transactionalExecutor.executeInTransaction {
            val result = workerAndApiInvocation(taskInformation, payload, actionWithResult, payloadReturnType, method)
            if (autoCompleteTask && completeBeforeCommit) {
              logger.trace { "PROCESS-ENGINE-WORKER-016: auto completing task ${taskInformation.taskId} before commit" }
              completeTask(taskInformation, result)
              processEngineWorkerMetrics.taskCompleted(topic)
            }
            result
          }
          if (autoCompleteTask && !completeBeforeCommit) {
            logger.trace { "PROCESS-ENGINE-WORKER-016: auto completing task ${taskInformation.taskId} after commit" }
            completeTask(taskInformation, requireNotNull(txResult))
            processEngineWorkerMetrics.taskCompleted(topic)
          }
        } else {
          val resultPayload = workerAndApiInvocation(taskInformation, payload, actionWithResult, payloadReturnType, method)
          if (autoCompleteTask) {
            logger.trace { "PROCESS-ENGINE-WORKER-016: auto completing task ${taskInformation.taskId} (there is and was no transaction)" }
            completeTask(taskInformation, resultPayload)
            processEngineWorkerMetrics.taskCompleted(topic)
          }
        }
      } catch (e: Exception) {
        handleAndReportException(taskInformation, e, topic)
      }
    },
    termination = {
      logger.debug { "PROCESS-ENGINE-WORKER-010: Terminating task ${it.taskId} from topic $topic" }
    }
  )

  /*
   * Encapsulates as a function to call it directly or inside of transaction.
   * Includes idempotency protection and returns the result right away, if already invoked.
   */
  internal fun workerAndApiInvocation(
    taskInformation: TaskInformation,
    payload: Map<String, Any?>,
    actionWithResult: TaskHandlerWithResult,
    payloadReturnType: Boolean,
    method: Method
  ): Map<String, Any?> {
    var result = idempotencyRegistry.getTaskResult(taskInformation)
    if (result == null) {
      logger.trace { "PROCESS-ENGINE-WORKER-015: invoking external task worker for ${taskInformation.taskId}" }
      val typedResult = actionWithResult.invoke(taskInformation, payload)
      logger.trace { "PROCESS-ENGINE-WORKER-017: successfully invoked external task worker for ${taskInformation.taskId}" }
      // convert
      result = if (payloadReturnType) {
        resultResolver.resolve(method = method, result = typedResult)
      } else {
        mapOf()
      }
      idempotencyRegistry.register(taskInformation, result)
    }
    return result
  }

  /*
   * Completes the task.
   */
  private fun completeTask(taskInformation: TaskInformation, payload: Map<String, Any?>) {
    taskCompletionApi.completeTask(CompleteTaskCmd(taskInformation.taskId) { payload }).get()
    if (configuration.removeTaskResultOnCompletion) {
      logger.debug { "PROCESS-ENGINE-WORKER-018: Removing result of task ${taskInformation.taskId}" }
      idempotencyRegistry.removeTaskResult(taskInformation.taskId)
    }
  }

  /*
   * Encapsulate error detection and reporting.
   */
  private fun handleAndReportException(taskInformation: TaskInformation, e: Exception, topic: String) {
    val cause = exceptionResolver.getCause(e)
    if (cause is BpmnErrorOccurred) {
      try {
        taskCompletionApi.completeTaskByError(
          CompleteTaskByErrorCmd(
            taskId = taskInformation.taskId,
            errorCode = cause.errorCode,
            errorMessage = cause.message,
            payloadSupplier = { cause.payload }
          )
        ).get()
        processEngineWorkerMetrics.taskCompletedByError(topic)
        logger.trace { "PROCESS-ENGINE-WORKER-012: external task worker thrown an BPMN Error ${cause.errorCode}" }
      } catch (ee: ExecutionException) {
        cause.addSuppressed(exceptionResolver.getCause(ee))
        logger.error(cause) { "PROCESS-ENGINE-WORKER-011: Exception while reporting BPMN Error for external task worker" }
      }
    } else {
      try {
        val retry = calculateRetry(taskInformation = taskInformation, cause = cause)
        taskCompletionApi.failTask(
          FailTaskCmd(
            taskId = taskInformation.taskId,
            reason = cause.message ?: "Exception during execution of external task worker",
            errorDetails = cause.stackTraceToString(),
            retryCount = retry.retryCount,
            retryBackoff = retry.retryBackoff
          )
        ).get()
        processEngineWorkerMetrics.taskFailed(topic)
      } catch (ee: ExecutionException) {
        cause.addSuppressed(exceptionResolver.getCause(ee))
      } finally {
        logger.error(cause) { "PROCESS-ENGINE-WORKER-011: Exception during execution of external task worker" }
      }
    }
  }

  /**
   * Calculates the retry information for a failed task.
   * @param taskInformation task information.
   * @param cause cause of the failure.
   * @return retry information.
   */
  fun calculateRetry(taskInformation: TaskInformation, cause: Throwable): FailureRetry {
    val retryCount = if (cause is FailJobException) {
      cause.retryCount
    } else {
      taskInformation.getMetaValueAsInt(TaskInformation.RETRIES)?.let { it - 1 }
    }
    val retryBackoff = if (cause is FailJobException) {
      cause.retryBackoff
    } else {
      null
    }
    return FailureRetry(
      retryCount = retryCount,
      retryBackoff = retryBackoff
    )
  }

  /**
   * Determines if the task should be completed before the commit of the transaction.
   * @param complete completion strategy of the worker.
   * @return true, if the task should be completed before commit.
   */
  fun completeBeforeCommit(complete: Completion): Boolean =
    if (complete == DEFAULT) {
      configuration.completeTasksBeforeCommit
    } else {
      complete == BEFORE_COMMIT
    }

  /**
   * Task handler as a function.
   */
  fun interface TaskHandlerWithResult : (TaskInformation, Map<String, Any?>) -> Any?

  /**
   * Failure retry information.
   */
  data class FailureRetry(
    val retryCount: Int?,
    val retryBackoff: Duration?
  )

}
