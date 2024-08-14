package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.converter.VariableConverter
import dev.bpmcrafters.processengine.worker.converter.VariableConverterConfiguration
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
@AutoConfigureAfter(VariableConverterConfiguration::class)
class ProcessEngineStarterRegistrar(
  @Lazy
  private val taskSubscriptionApi: TaskSubscriptionApi,
  @Lazy
  private val taskCompletionApi: ExternalTaskCompletionApi,
  @Lazy
  private val variableConverter: VariableConverter,
  @Lazy
  private val parameterResolver: ParameterResolver
) : BeanPostProcessor {

  companion object : KLogging()

  override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
    val annotatedProcessEngineWorkers = bean.getAnnotatedWorkers()
    annotatedProcessEngineWorkers.map { method ->

      val topic = method.getTopic()
      val annotatedVariableParameters = method.parameters.filter { it.isVariable() }

      val subscription = taskSubscriptionApi.subscribeForTask(
        subscribe(
          topic,
          if (annotatedVariableParameters.isNotEmpty()) {
            annotatedVariableParameters.extractVariableNames()
          } else {
            null
          },
          method.hasPayloadReturnType()
        ) { taskInformation, payload ->
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
   * @param returnsMap flag indicating if the method will return the result which needs to be passed to the completion API.
   * @param actionWithResult worker always returning the result.
   */
  private fun subscribe(
    topic: String,
    payloadDescription: Set<String>? = emptySet(),
    returnsMap: Boolean,
    actionWithResult: TaskHandlerWithResult
  ): SubscribeForTaskCmd = SubscribeForTaskCmd(
    restrictions = mapOf(),
    taskType = TaskType.EXTERNAL,
    taskDescriptionKey = topic,
    payloadDescription = payloadDescription,
    action = { taskInformation, payload ->
      try {
        actionWithResult.invoke(taskInformation, payload).also { result ->
          if (returnsMap) {
            taskCompletionApi.completeTask(
              CompleteTaskCmd(
                taskId = taskInformation.taskId
              ) {
                if (result != null) {
                  @Suppress("UNCHECKED_CAST")
                  result as Map<String, Any>
                } else {
                  mapOf()
                }
              }
            )
          }
        }
      } catch (e: Exception) {
        if (e.cause != null && e.cause is BpmnErrorOccurred) {
          val cause = e.cause as BpmnErrorOccurred
          taskCompletionApi.completeTaskByError(
            CompleteTaskByErrorCmd(
              taskId = taskInformation.taskId,
              errorCode = cause.errorCode,
              payloadSupplier = { cause.payload }
            )
          )
        } else {
          taskCompletionApi.failTask(
            FailTaskCmd(
              taskId = taskInformation.taskId,
              reason = e.message ?: "Exception during execution of external task worker",
              errorDetails = e.stackTraceToString()
            )
          )
        }
      }
    },
    termination = {
      logger.debug { "Terminating task from topic $topic." }
    }
  )

  fun interface TaskHandlerWithResult : (TaskInformation, Map<String, Any>) -> Any?
}
