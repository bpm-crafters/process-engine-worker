package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred
import dev.bpmcrafters.processengine.worker.FailJobException
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker.Completion
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker.Completion.BEFORE_COMMIT
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker.Completion.DEFAULT
import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.idempotency.NoOpIdempotencyRegistry
import dev.bpmcrafters.processengine.worker.registrar.metrics.ProcessEngineWorkerMetricsNoOp
import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.task.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.lang.reflect.Method
import java.time.Duration
import java.util.concurrent.ExecutionException

private val logger = KotlinLogging.logger {}

/**
 * Registrar responsible for collecting process engine workers and creating corresponding external task subscriptions.
 * @since 0.0.3
 */
class ProcessEngineWorkerRegistrar(
  private val options: WorkerRuntimeOptions,
  private val taskSubscriptionApi: TaskSubscriptionApi,
  private val taskCompletionApi: ServiceTaskCompletionApi,
  private val variableConverter: VariableConverter,
  private val parameterResolver: ParameterResolver,
  private val resultResolver: ResultResolver,
  private val transactionExecutor: WorkerTransactionExecutor,
  private val processEngineWorkerMetrics: ProcessEngineWorkerMetrics,
  private val idempotencyRegistry: IdempotencyRegistry
) {

  /** Creates a runtime for ordinary, non-transactional objects with default resolvers and no caching. */
  constructor(
    taskSubscriptionApi: TaskSubscriptionApi,
    taskCompletionApi: ServiceTaskCompletionApi,
    variableConverter: VariableConverter
  ) : this(
    WorkerRuntimeOptions(), taskSubscriptionApi, taskCompletionApi, variableConverter,
    ParameterResolver.builder().build(), ResultResolver.builder().build(),
    WorkerTransactionExecutor { throw IllegalStateException("No transaction executor configured") },
    ProcessEngineWorkerMetricsNoOp,
    NoOpIdempotencyRegistry()
  )

  private val exceptionResolver = ExceptionResolver()

  /** Registers public annotated methods on an ordinary object, without framework interception. */
  fun register(worker: Any) {
    worker.getAnnotatedWorkers().forEach { method ->
      register(method, requireNotNull(method.workerMetadata()), false) { arguments -> method.invoke(worker, *arguments) }
    }
  }

  /** Registers metadata and an invoker independently, allowing adapters to preserve managed proxies. */
  fun register(method: Method, isTransactional: Boolean, invoker: WorkerInvoker) {
    register(method, requireNotNull(method.workerMetadata()), isTransactional, invoker)
  }

  /** Keeps concrete parameter/result types separate from inherited annotation metadata. */
  fun register(method: Method, metadataMethod: Method, isTransactional: Boolean, invoker: WorkerInvoker) {
    val topic = metadataMethod.getTopic()
    val payloadReturnType = resultResolver.payloadReturnType(method)
    val autoCompleteTask = metadataMethod.getAutoComplete()

    if (autoCompleteTask && !method.hasVoidReturnType() && !payloadReturnType) {
      logger.warn { "PROCESS-ENGINE-WORKER-002: Found an unambiguous process task worker defined in ${method.declaringClass.name}#${method.name} having non-void and not payload compatible return type and auto-complete set to true." }
    }

    val annotatedVariableParameters = method.parameters.mapIndexed { index, parameter ->
      if (parameter.isVariable()) parameter else metadataMethod.parameters[index]
    }.filter { it.isVariable() }
    val variableNames = if (annotatedVariableParameters.isNotEmpty()) {
      annotatedVariableParameters.extractVariableNames()
    } else {
      null // null means no limitation
    }

    val subscription = taskSubscriptionApi.subscribeForTask(
      subscribe(
        topic = topic,
        payloadDescription = variableNames,
        restrictions = restrictions(metadataMethod),
        autoCompleteTask = autoCompleteTask,
        completion = metadataMethod.getCompletion(),
        isTransactional = isTransactional,
        payloadReturnType = payloadReturnType,
        method = method,
      ) { taskInformation, payload ->
        val args = parameterResolver.createInvocationArguments(
          method = method,
          metadataMethod = metadataMethod,
          taskInformation = taskInformation,
          payload = payload,
          variableConverter = variableConverter,
          taskCompletionApi = taskCompletionApi
        )
        invoker.invoke(args)
      }
    )
    subscription.get()
  }

  private fun restrictions(method: Method): Map<String, String> = buildMap {
    method.getLockDuration()?.let { put(CommonRestrictions.WORKER_LOCK_DURATION_IN_MILLISECONDS, it.toString()) }
    (method.getTenantId() ?: options.tenantId)?.let { put(CommonRestrictions.TENANT_ID, it) }
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
          val txResult = transactionExecutor.execute {
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
            completeTask(taskInformation, txResult)
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
  private fun workerAndApiInvocation(
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
    if (options.removeTaskResultOnCompletion) {
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

  internal fun calculateRetry(taskInformation: TaskInformation, cause: Throwable): FailureRetry {
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

  internal fun completeBeforeCommit(complete: Completion): Boolean =
    if (complete == DEFAULT) {
      options.completeTasksBeforeCommit
    } else {
      complete == BEFORE_COMMIT
    }

  /**
   * Task handler as a function.
   */
  private fun interface TaskHandlerWithResult : (TaskInformation, Map<String, Any?>) -> Any?

  /**
   * Failure retry information.
   */
  internal data class FailureRetry(
    val retryCount: Int?,
    val retryBackoff: Duration?
  )

}
