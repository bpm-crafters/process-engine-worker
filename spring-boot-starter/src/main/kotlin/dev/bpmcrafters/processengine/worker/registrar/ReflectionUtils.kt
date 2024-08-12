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

fun Parameter.isPayload() = this.type.isAssignableFrom(Map::class.java)
  && (this.parameterizedType as ParameterizedType).isMapOfStringObject()

fun Parameter.isTaskInformation() = this.type.isAssignableFrom(TaskInformation::class.java)
fun Parameter.isTaskCompletionApiParameter() = this.type.isAssignableFrom(ExternalTaskCompletionApi::class.java)

fun Parameter.isVariable() = this.isAnnotationPresent(Variable::class.java)

fun Parameter.extractVariableName() = this.getAnnotation(Variable::class.java).name

fun List<Parameter>.extractVariableNames(): Set<String> = this.map { it.extractVariableName() }.toSet()

fun Method.hasPayloadReturnType() = this.returnType.isAssignableFrom(Map::class.java)
  && (this.genericReturnType as ParameterizedType).isMapOfStringObject()

private fun ParameterizedType.isMapOfStringObject() = this.actualTypeArguments.let {
  it.size == 2
    && it[0].typeName == "java.lang.String"
    && (it[1].typeName == "java.lang.Object"
    || (it[1] is WildcardType
    && (it[1] as WildcardType).upperBounds.size == 1
    && (it[1] as WildcardType).upperBounds[0].typeName == "java.lang.Object")
    )
}

fun Any.getAnnotatedWorkers() = AopUtils.getTargetClass(this).methods.filter { m -> m.isAnnotationPresent(ProcessEngineWorker::class.java) }

fun Method.getTopic(): String {
  val topicAnnotation = this.getAnnotation(ProcessEngineWorker::class.java)
  return if (topicAnnotation.topic != DEFAULT_UNSET_TOPIC) {
    topicAnnotation.topic
  } else {
    this.name
  }
}

