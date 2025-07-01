package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred
import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerAutoConfiguration
import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerProperties.Companion.PREFIX
import dev.bpmcrafters.processengineapi.task.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.transaction.support.TransactionTemplate
import java.lang.reflect.Method
import java.util.concurrent.ExecutionException


private val logger = KotlinLogging.logger {}

/**
 * Registrar responsible for collecting process engine workers and creating corresponding external task subscriptions.
 * @since 0.0.3
 */
@Configuration
@ConditionalOnProperty(prefix = PREFIX, name = ["enabled"], havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(ProcessEngineWorkerAutoConfiguration::class)
class ProcessEngineStarterRegistrar(
  @Lazy
  private val taskSubscriptionApi: TaskSubscriptionApi,
  @Lazy
  private val taskCompletionApi: ServiceTaskCompletionApi,
  @Lazy
  private val variableConverter: VariableConverter,
  @Lazy
  private val parameterResolver: ParameterResolver,
  @Lazy
  private val resultResolver: ResultResolver,
  @Lazy
  private val transactionalTemplate: TransactionTemplate
) : BeanPostProcessor {

  private val exceptionResolver = ExceptionResolver()

  override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
    val annotatedProcessEngineWorkers = bean.getAnnotatedWorkers()

    if (annotatedProcessEngineWorkers.isNotEmpty()) {
      logger.debug { "PROCESS-ENGINE-WORKER-001: Detected ${annotatedProcessEngineWorkers.size} annotated workers on $beanName." }
      logger.trace { "PROCESS-ENGINE-WORKER-001: Detected annotated workers on $beanName are: ${annotatedProcessEngineWorkers.map { it.name }}." }
    }
    annotatedProcessEngineWorkers.map { method ->

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

      // check if the method or class is marked to run in transaction
      val isTransactional = method.isTransactional()

      val subscription = taskSubscriptionApi.subscribeForTask(
        subscribe(
          topic = topic,
          payloadDescription = variableNames,
          autoCompleteTask = autoCompleteTask,
          isTransactional = isTransactional,
          payloadReturnType = payloadReturnType,
          method = method
        ) { taskInformation, payload ->
          val args: Array<Any?> = parameterResolver.createInvocationArguments(
            method = method,
            taskInformation = taskInformation,
            payload = payload,
            variableConverter = variableConverter,
            taskCompletionApi = taskCompletionApi
          )
          method.invoke(bean, *args) // spread the array
        }
      )
      subscription.get()
    }
    return bean
  }

  /**
   * Executes the subscription.
   * @param topic subscription topic.
   * @param payloadDescription description of the variables to be passed.
   * @param autoCompleteTask flag indicating if the task should be completed after execution of the worker.
   * @param isTransactional flag indicating if the task worker and task completion should run in a transaction.
   * @param payloadReturnType flag indicating of the return type of the method can be converted int payload.
   * @param method process engine worker method.
   * @param actionWithResult worker always returning the result.
   */
  private fun subscribe(
    topic: String,
    payloadDescription: Set<String>? = emptySet(),
    autoCompleteTask: Boolean,
    isTransactional: Boolean,
    payloadReturnType: Boolean,
    method: Method,
    actionWithResult: TaskHandlerWithResult
  ): SubscribeForTaskCmd = SubscribeForTaskCmd(
    restrictions = mapOf(),
    taskType = TaskType.EXTERNAL,
    taskDescriptionKey = topic,
    payloadDescription = payloadDescription,
    action = { taskInformation, payload ->

      /*
       * encapsulate as a function and call it directly or inside of transaction
       */
      fun workerAndApiInvocation() {
        logger.trace { "PROCESS-ENGINE-WORKER-015: invoking external task worker for ${taskInformation.taskId}" }
        // execute worker
        val result = actionWithResult.invoke(taskInformation, payload)
        // complete
        if (autoCompleteTask) {
          logger.trace { "PROCESS-ENGINE-WORKER-016: auto completing task ${taskInformation.taskId}" }
          taskCompletionApi.completeTask(
            CompleteTaskCmd(
              taskId = taskInformation.taskId
            ) {
              if (payloadReturnType) {
                resultResolver.resolve(method = method, result = result)
              } else {
                mapOf()
              }
            }
          ).get()
        }
        logger.trace { "PROCESS-ENGINE-WORKER-017: successfully invoked external task worker for ${taskInformation.taskId}" }
      }

      try {
        // depending on transactional annotations, execute either in a new transaction or direct
        if (isTransactional) {
          transactionalTemplate.executeWithoutResult {
            workerAndApiInvocation()
          }
        } else {
          workerAndApiInvocation()
        }
      } catch (e: Exception) {
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
            logger.trace { "PROCESS-ENGINE-WORKER-012: external task worker thrown an BPMN Error ${cause.errorCode}" }
          } catch (ee: ExecutionException) {
            cause.addSuppressed(exceptionResolver.getCause(ee))
            logger.error(cause) { "PROCESS-ENGINE-WORKER-011: Exception while reporting BPMN Error for external task worker" }
          }
        } else {
          try {
            taskCompletionApi.failTask(
              FailTaskCmd(
                taskId = taskInformation.taskId,
                reason = cause.message ?: "Exception during execution of external task worker",
                errorDetails = cause.stackTraceToString()
              )
            ).get()
          } catch (ee: ExecutionException) {
            cause.addSuppressed(exceptionResolver.getCause(ee))
          } finally {
            logger.error(cause) { "PROCESS-ENGINE-WORKER-011: Exception during execution of external task worker" }
          }
        }
      }
    },
    termination = {
      logger.debug { "PROCESS-ENGINE-WORKER-010: Terminating task ${it.taskId} from topic $topic" }
    }
  )

  /**
   * Task handler as a function.
   */
  fun interface TaskHandlerWithResult : (TaskInformation, Map<String, Any>) -> Any?
}
