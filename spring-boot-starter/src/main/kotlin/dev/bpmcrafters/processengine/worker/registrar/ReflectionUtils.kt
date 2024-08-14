package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker.Companion.DEFAULT_UNSET_TOPIC
import dev.bpmcrafters.processengine.worker.Variable
import dev.bpmcrafters.processengineapi.task.ExternalTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.springframework.aop.support.AopUtils
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.WildcardType

/**
 * Checks if the method parameter is payload of type Map<String, Any> or compatible.
 */
fun Parameter.isPayload() = this.type.isAssignableFrom(Map::class.java)
  && (this.parameterizedType as ParameterizedType).isMapOfStringObject()

/**
 * Checks if the parameter is task information.
 */
fun Parameter.isTaskInformation() = this.type.isAssignableFrom(TaskInformation::class.java)

/**
 * Checks if parameter is ExternalTaskCompletionApi
 */
fun Parameter.isTaskCompletionApiParameter() = this.type.isAssignableFrom(ExternalTaskCompletionApi::class.java)

/**
 * Checks if the parameter is annotated with a Variable annotation.
 */
fun Parameter.isVariable() = this.isAnnotationPresent(Variable::class.java)

/**
 * Extracts variable name from the variable annotation of the parameter.
 */
fun Parameter.extractVariableName() = this.getAnnotation(Variable::class.java).name

/**
 * Extract variable names from all parameters.
 */
fun List<Parameter>.extractVariableNames(): Set<String> = this.map { it.extractVariableName() }.toSet()

/**
 * Checks if the method has a return type compatible with payload of type Map<String, Any>
 */
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

/**
 * Retrieves list of worker methods.
 */
fun Any.getAnnotatedWorkers(): List<Method> = AopUtils
  .getTargetClass(this)
  .methods
  .filter { m -> m.isAnnotationPresent(ProcessEngineWorker::class.java) }

/**
 * Detects worker topic either using the annotation or defaulting to method name.
 */
fun Method.getTopic(): String {
  val topicAnnotation = this.getAnnotation(ProcessEngineWorker::class.java)
  return if (topicAnnotation.topic != DEFAULT_UNSET_TOPIC) {
    topicAnnotation.topic
  } else {
    this.name
  }
}

