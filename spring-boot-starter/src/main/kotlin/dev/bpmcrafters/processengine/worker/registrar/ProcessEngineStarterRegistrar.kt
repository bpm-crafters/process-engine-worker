package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred
import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerAutoConfiguration
import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerProperties.Companion.PREFIX
import dev.bpmcrafters.processengineapi.task.*
import mu.KLogging
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import java.lang.reflect.Method


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
  private val resultResolver: ResultResolver
) : BeanPostProcessor {

  private val exceptionResolver = ExceptionResolver()

  companion object : KLogging()

  override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
    val annotatedProcessEngineWorkers = bean.getAnnotatedWorkers()

    if (annotatedProcessEngineWorkers.isNotEmpty()) {
      logger.debug { "PROCESS-ENGINE_WORKER-001: Detected annotated workers on $beanName." }
    }
    annotatedProcessEngineWorkers.map { method ->

      val topic = method.getTopic()
      val payloadReturnType = resultResolver.payloadReturnType(method)
      val autoCompleteTask = method.getAutoComplete()

      // report misconfiguration because: the user selected to autocomplete, there is no result converter and the method has a non-void result.
      // so probably this result is not converted as payload - and either there should be no result
      // or there should be a matching converter strategy
      if (autoCompleteTask && !method.hasVoidReturnType() && !payloadReturnType) {
        logger.warn { "PROCESS-ENGINE_WORKER-002: Found an unambiguous process task worker defined in $beanName#${method.name} having non-void and not payload compatible return type and auto-complete set to true." }
      }

      val annotatedVariableParameters = method.parameters.filter { it.isVariable() }

      val variableNames = if (annotatedVariableParameters.isNotEmpty()) {
        annotatedVariableParameters.extractVariableNames() // explicit variable names
      } else {
        null // null means no limitation
      }

      val subscription = taskSubscriptionApi.subscribeForTask(
        subscribe(topic, variableNames, autoCompleteTask, payloadReturnType, method)
        { taskInformation, payload ->
          val args: Array<Any> = parameterResolver.createInvocationArguments(
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
   * @param topic subscription topic
   * @param payloadDescription description of the variables to be passed.
   * @param autoCompleteTask flag indicating if the task should be completed after execution of the worker.
   * @param payloadReturnType flag indicating of the return type of the method can be converted int payload.
   * @param method process engine worker method.
   * @param actionWithResult worker always returning the result.
   */
  private fun subscribe(
    topic: String,
    payloadDescription: Set<String>? = emptySet(),
    autoCompleteTask: Boolean,
    payloadReturnType: Boolean,
    method: Method,
    actionWithResult: TaskHandlerWithResult
  ): SubscribeForTaskCmd = SubscribeForTaskCmd(
    restrictions = mapOf(),
    taskType = TaskType.EXTERNAL,
    taskDescriptionKey = topic,
    payloadDescription = payloadDescription,
    action = { taskInformation, payload ->
      try {
        actionWithResult.invoke(taskInformation, payload).also { result ->
          if (autoCompleteTask) {
            taskCompletionApi.completeTask(
              CompleteTaskCmd(
                taskId = taskInformation.taskId
              ) {
                if (payloadReturnType) {
                  resultResolver.resolve(method, result)
                } else {
                  mapOf()
                }
              }
            ).get()
          }
        }
      } catch (e: Exception) {
        val cause = exceptionResolver.getCause(e)
        if (cause is BpmnErrorOccurred) {
          taskCompletionApi.completeTaskByError(
            CompleteTaskByErrorCmd(
              taskId = taskInformation.taskId,
              errorCode = cause.errorCode,
              errorMessage = cause.message,
              payloadSupplier = { cause.payload }
            )
          ).get()
        } else {
          taskCompletionApi.failTask(
            FailTaskCmd(
              taskId = taskInformation.taskId,
              reason = cause.message ?: "Exception during execution of external task worker",
              errorDetails = cause.stackTraceToString()
            )
          ).get()
        }
      }
    },
    termination = {
      logger.debug { "PROCESS-ENGINE_WORKER-010: Terminating task from topic $topic." }
    }
  )

  fun interface TaskHandlerWithResult : (TaskInformation, Map<String, Any>) -> Any?
}
