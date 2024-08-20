package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred
import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerAutoConfiguration
import dev.bpmcrafters.processengineapi.task.*
import mu.KLogging
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy


/**
 * Registrar responsible for collecting process engine workers and creating corresponding external tas subscriptions.
 */
@Configuration
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
) : BeanPostProcessor {

  private val exceptionResolver = ExceptionResolver()

  companion object : KLogging()

  override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
    val annotatedProcessEngineWorkers = bean.getAnnotatedWorkers()
    annotatedProcessEngineWorkers.map { method ->

      val topic = method.getTopic()
      val payloadReturnType = method.hasPayloadReturnType()
      val autoCompleteTask = method.getAutoComplete()

      if (autoCompleteTask && !method.hasVoidReturnType() && !payloadReturnType) {
        logger.warn { "Found an unambiguous process task worker defined in $beanName#${method.name} having non-void and not payload compatible return type and auto-complete set to true." }
      }

      val annotatedVariableParameters = method.parameters.filter { it.isVariable() }

      val variableNames = if (annotatedVariableParameters.isNotEmpty()) {
        annotatedVariableParameters.extractVariableNames() // explicit variable names
      } else {
        null // null means no limitation
      }

      val subscription = taskSubscriptionApi.subscribeForTask(
        subscribe(topic, variableNames, autoCompleteTask, payloadReturnType)
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
   * @param actionWithResult worker always returning the result.
   */
  private fun subscribe(
    topic: String,
    payloadDescription: Set<String>? = emptySet(),
    autoCompleteTask: Boolean,
    payloadReturnType: Boolean,
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
                if (payloadReturnType && result != null) {
                  @Suppress("UNCHECKED_CAST")
                  result as Map<String, Any>
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
              payloadSupplier = { cause.payload }
            )
          ).get()
        } else {
          taskCompletionApi.failTask(
            FailTaskCmd(
              taskId = taskInformation.taskId,
              reason = cause?.message ?: "Exception during execution of external task worker",
              errorDetails = cause?.stackTraceToString()
            )
          ).get()
        }
      }
    },
    termination = {
      logger.debug { "Terminating task from topic $topic." }
    }
  )

  fun interface TaskHandlerWithResult : (TaskInformation, Map<String, Any>) -> Any?
}
