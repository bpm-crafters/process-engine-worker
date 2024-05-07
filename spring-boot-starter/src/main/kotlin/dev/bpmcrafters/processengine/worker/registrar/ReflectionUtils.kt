package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker.Companion.DEFAULT_UNSET_TOPIC
import dev.bpmcrafters.processengine.worker.Variable
import dev.bpmcrafters.processengine.worker.converter.VariableConverter
import dev.bpmcrafters.processengineapi.task.ExternalTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.springframework.aop.support.AopUtils
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.WildcardType

fun Method.hasPayloadParameter(index: Int) = this.parameters.size > index
  && this.parameters[index].type.isAssignableFrom(Map::class.java)
  && (this.parameters[index].parameterizedType as ParameterizedType).isMapOfStringObject()


fun Method.hasPayloadReturnType() = this.returnType.isAssignableFrom(Map::class.java)
  && (this.genericReturnType as ParameterizedType).isMapOfStringObject()

private fun ParameterizedType.isMapOfStringObject() = this.actualTypeArguments.let {
  it.size == 2
    && it[0].typeName == "java.lang.String"
    && (it[1].typeName == "java.lang.Object"
    || (it[1] is WildcardType && (it[1] as WildcardType).upperBounds.size == 1 && (it[1] as WildcardType).upperBounds[0].typeName == "java.lang.Object")
    )
}

fun Method.hasTaskInformationParameter(index: Int) = this.parameters.size > index
  && this.parameters[index].type.isAssignableFrom(TaskInformation::class.java)

fun Method.hasTaskCompletionApiParameter(index: Int) = this.parameters.size > index
  && this.parameters[index].type.isAssignableFrom(ExternalTaskCompletionApi::class.java)

fun Any.getAnnotatedWorkers() = AopUtils.getTargetClass(this).methods.filter { m -> m.isAnnotationPresent(ProcessEngineWorker::class.java) }

fun Method.getTopic(): String {
  val topicAnnotation = this.getAnnotation(ProcessEngineWorker::class.java)
  return if (topicAnnotation.topic != DEFAULT_UNSET_TOPIC) {
    topicAnnotation.topic
  } else {
    this.name
  }
}

fun Parameter.extractVariableName() = this.getAnnotation(Variable::class.java).name

fun List<Parameter>.extractVariableNames(): Set<String> = this.map { it.extractVariableName() }.toSet()

fun List<Parameter>.constructInvocation(jsonMapper: VariableConverter, payload: Map<String, Any>, taskInformation: TaskInformation?, taskCompletionApi: ExternalTaskCompletionApi?): Array<Any> {

  val variablesNames = this.extractVariableNames()
  require(payload.keys.containsAll(variablesNames)) { "Expected payload to contain variables $variablesNames, but it contained ${payload.keys}" }

  val listOfParams = this.map { param -> jsonMapper.mapToType(payload[param.extractVariableName()], param.type) }

  return if (taskInformation == null) {
    if (taskCompletionApi != null) {
      listOf(taskCompletionApi) + listOfParams
    } else {
      listOfParams
    }
  } else {
    if (taskCompletionApi != null) {
      listOf(taskInformation, taskCompletionApi) + listOfParams
    } else {
      listOf(taskInformation) + listOfParams
    }
  }.toTypedArray()
}
