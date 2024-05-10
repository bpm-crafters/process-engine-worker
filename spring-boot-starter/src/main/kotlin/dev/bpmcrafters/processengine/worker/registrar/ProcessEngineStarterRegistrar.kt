package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred
import dev.bpmcrafters.processengine.worker.Variable
import dev.bpmcrafters.processengine.worker.converter.VariableConverter
import dev.bpmcrafters.processengine.worker.converter.VariableConverterConfiguration
import dev.bpmcrafters.processengineapi.task.*
import mu.KLogging
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy


/**
 * Registrar responsible for collecting process engine workers.
 */
@Configuration
@AutoConfigureAfter(VariableConverterConfiguration::class)
class ProcessEngineStarterRegistrar(
  @Lazy
  private val taskSubscriptionApi: TaskSubscriptionApi,
  @Lazy
  private val taskCompletionApi: ExternalTaskCompletionApi,
  @Lazy
  private val variableConverter: VariableConverter
) : BeanPostProcessor {

  companion object : KLogging()

  // FIXME -> parameter resolver instead of positioned parameters
  override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
    val annotatedProcessEngineWorkers = bean.getAnnotatedWorkers()
    annotatedProcessEngineWorkers.map { method ->

      val topic = method.getTopic()

      val annotatedVariableParameters = method.parameters.filter { it.isAnnotationPresent(Variable::class.java) }

      val subscription = if (annotatedVariableParameters.isNotEmpty()) {
        // explicit variable parameters
        if (method.hasTaskInformationParameter(0)) {
          if (method.hasTaskCompletionApiParameter(1)) {
            logger.trace { "${method.name} task info at 0, completion at 1, variables parameters ${annotatedVariableParameters.extractVariableNames()}" }
            taskSubscriptionApi.subscribeForTask(
              subscribe(
                topic,
                annotatedVariableParameters.extractVariableNames(),
                method.hasPayloadReturnType()
              ) { taskInformation, payload -> method.invoke(bean, *annotatedVariableParameters.constructInvocation(variableConverter, payload, taskInformation, taskCompletionApi)) }
            )
          } else {
            logger.trace { "${method.name} task info at 0, variables parameters ${annotatedVariableParameters.extractVariableNames()}" }
            taskSubscriptionApi.subscribeForTask(
              subscribe(
                topic,
                annotatedVariableParameters.extractVariableNames(),
                method.hasPayloadReturnType()
              ) { taskInformation, payload -> method.invoke(bean, *annotatedVariableParameters.constructInvocation(variableConverter, payload, taskInformation, null)) }
            )
          }
        } else {
          if (method.hasTaskCompletionApiParameter(0)) {
            logger.trace { "${method.name} completion at 0,  variables parameters ${annotatedVariableParameters.extractVariableNames()}" }
            taskSubscriptionApi.subscribeForTask(
              subscribe(
                topic,
                annotatedVariableParameters.extractVariableNames(),
                method.hasPayloadReturnType()
              )
              { _, payload -> method.invoke(bean, *annotatedVariableParameters.constructInvocation(variableConverter, payload, null, taskCompletionApi)) }
            )
          } else {
            logger.trace { "${method.name} variables parameters ${annotatedVariableParameters.extractVariableNames()}" }
            taskSubscriptionApi.subscribeForTask(
              subscribe(
                topic,
                annotatedVariableParameters.extractVariableNames(),
                method.hasPayloadReturnType()
              )
              { _, payload -> method.invoke(bean, *annotatedVariableParameters.constructInvocation(variableConverter, payload, null, null)) }
            )
          }
        }
      } else {
        if (method.hasTaskInformationParameter(0)) {
          if (method.hasTaskCompletionApiParameter(1)) {
            if (method.hasPayloadParameter(2)) {
              logger.trace { "${method.name} task info at 0, task completion at 1, payload on 2" }
              taskSubscriptionApi.subscribeForTask(
                subscribe(
                  topic,
                  null,
                  method.hasPayloadReturnType()
                )
                { taskInformation, payload -> method.invoke(bean, taskInformation, taskCompletionApi, payload) }
              )
            } else {
              logger.trace { "${method.name} task info at 0, task completion at 1" }
              taskSubscriptionApi.subscribeForTask(
                subscribe(
                  topic,
                  emptySet(),
                  method.hasPayloadReturnType()
                )
                { taskInformation, _ -> method.invoke(bean, taskInformation, taskCompletionApi) }
              )
            }
          } else { // no completion on 1
            if (method.hasPayloadParameter(1)) {
              logger.trace { "${method.name} task info at 0, payload on 1" }
              taskSubscriptionApi.subscribeForTask(
                subscribe(
                  topic,
                  null,
                  method.hasPayloadReturnType(),
                )
                { taskInformation, payload -> method.invoke(bean, taskInformation, payload) }
              )
            } else {
              logger.trace { "${method.name} task info at 0" }
              taskSubscriptionApi.subscribeForTask(
                subscribe(
                  topic,
                  emptySet(),
                  method.hasPayloadReturnType(),
                ) { taskInformation, _ -> method.invoke(bean, taskInformation) }
              )
            }
          }
        } else if (method.hasTaskCompletionApiParameter(0)) {
          if (method.hasPayloadParameter(1)) {
            logger.trace { "${method.name} completion at 0, payload on 1" }
            taskSubscriptionApi.subscribeForTask(
              subscribe(
                topic,
                null,
                method.hasPayloadReturnType(),
              ) { _, payload -> method.invoke(bean, taskCompletionApi, payload) }
            )
          } else {
            logger.trace { "${method.name} completion at 0" }
            taskSubscriptionApi.subscribeForTask(
              subscribe(
                topic,
                emptySet(),
                method.hasPayloadReturnType(),
              ) { _, _ -> method.invoke(bean, taskCompletionApi) }
            )
          }
        } else if (method.hasPayloadParameter(0)) {
          logger.trace { "${method.name} payload at 0" }
          taskSubscriptionApi.subscribeForTask(
            subscribe(
              topic,
              null,
              method.hasPayloadReturnType(),
            ) { _, payload -> method.invoke(bean, payload) }
          )
        } else if (method.parameters.isEmpty()) {
          logger.trace { "${method.name} no params" }
          taskSubscriptionApi.subscribeForTask(
            subscribe(
              topic,
              emptySet(),
              method.hasPayloadReturnType(),
            ) { _, _ -> method.invoke(bean) }
          )
        } else {
          throw IllegalArgumentException("Found a method with some unsupported parameters annotated with @ProcessEngineWorker.")
        }
      }
      subscription.get()
    }
    return bean
  }

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
        // logger.error(e) { "Error executing process task ${taskInformation.taskId}" }
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


