@file:JvmName("ReflectionUtilsKt")

package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker.Completion
import org.springframework.aop.support.AopUtils
import org.springframework.core.BridgeMethodResolver
import java.lang.reflect.Method
import java.lang.reflect.Parameter

// Keep the original static JVM owner and descriptors for precompiled starter clients and Java imports.
// Forwarding functions use distinct Kotlin names where the core already provides an extension.
/**
 * Checks whether the parameter accepts the process payload map.
 **/
@JvmName("isPayload")
fun legacyIsPayload(parameter: Parameter): Boolean = parameter.isPayload()

/**
 * Checks whether the parameter accepts task information.
 **/
@JvmName("isTaskInformation")
fun legacyIsTaskInformation(parameter: Parameter): Boolean = parameter.isTaskInformation()

/**
 * Checks whether the parameter accepts a variable converter.
 **/
@JvmName("isVariableConverter")
fun legacyIsVariableConverter(parameter: Parameter): Boolean = parameter.isVariableConverter()

/**
 * Checks whether the parameter accepts the service-task completion API.
 **/
@JvmName("isTaskCompletionApiParameter")
fun legacyIsTaskCompletionApiParameter(parameter: Parameter): Boolean = parameter.isTaskCompletionApiParameter()

/**
 * Checks whether the parameter carries a variable annotation.
 **/
@JvmName("isVariable")
fun legacyIsVariable(parameter: Parameter): Boolean = parameter.isVariable()

/**
 * Resolves the process variable name from an annotated parameter.
 **/
@JvmName("extractVariableName")
fun legacyExtractVariableName(parameter: Parameter): String = parameter.extractVariableName()

/**
 * Resolves the converter declared for an annotated variable or uses the supplied default.
 **/
@JvmName("extractVariableConverter")
fun legacyExtractVariableConverter(parameter: Parameter, defaultVariableConverter: VariableConverter): VariableConverter =
  parameter.extractVariableConverter(defaultVariableConverter)

/**
 * Returns whether the annotated variable is required.
 **/
@JvmName("extractVariableMandatoryFlag")
fun legacyExtractVariableMandatoryFlag(parameter: Parameter): Boolean = parameter.extractVariableMandatoryFlag()

/**
 * Checks whether the parameter is a Java Optional.
 **/
@JvmName("isOptional")
fun legacyIsOptional(parameter: Parameter): Boolean = parameter.isOptional()

/**
 * Resolves the process variable names for the given parameters.
 **/
@JvmName("extractVariableNames")
fun legacyExtractVariableNames(parameters: List<Parameter>): Set<String> = parameters.extractVariableNames()

/**
 * Checks whether the method returns a supported payload map.
 **/
@JvmName("hasPayloadReturnType")
fun legacyHasPayloadReturnType(method: Method): Boolean = method.hasPayloadReturnType()

/**
 * Checks whether the method has a void return type.
 **/
@JvmName("hasVoidReturnType")
fun legacyHasVoidReturnType(method: Method): Boolean = method.hasVoidReturnType()

/**
 * Discovers annotated worker methods on the target class of a Spring bean.
 **/
fun Any.getAnnotatedWorkers(): List<Method> {
  val targetClass = AopUtils.getTargetClass(this)
  return targetClass.methods.map { BridgeMethodResolver.findBridgedMethod(it) }.distinct()
    .filter { !it.isSynthetic && it.workerMetadata(targetClass) != null }
}

/**
 * Discovers worker methods for callers using the explicitly named legacy helper.
 **/
fun legacyGetAnnotatedWorkers(worker: Any): List<Method> = worker.getAnnotatedWorkers()

/**
 * Resolves the topic of an annotated worker method.
 **/
@JvmName("getTopic")
fun legacyGetTopic(method: Method): String = method.getTopic()

/**
 * Returns whether the annotated worker automatically completes its tasks.
 **/
@JvmName("getAutoComplete")
fun legacyGetAutoComplete(method: Method): Boolean = method.getAutoComplete()

/**
 * Returns when the annotated worker completes tasks relative to transaction commit.
 **/
@JvmName("getCompletion")
fun legacyGetCompletion(method: Method): Completion = method.getCompletion()

/**
 * Returns the worker lock duration in milliseconds if configured.
 **/
@JvmName("getLockDuration")
fun legacyGetLockDuration(method: Method): Long? = method.getLockDuration()

/**
 * Returns the worker tenant identifier if configured.
 **/
@JvmName("getTenantId")
fun legacyGetTenantId(method: Method): String? = method.getTenantId()

/**
 * Checks whether Spring resolves a transaction attribute that enables the worker transaction wrapper.
 **/
@JvmName("isTransactional")
fun legacyIsTransactional(method: Method): Boolean = method.isTransactional()

/**
 * Finds the method that supplies the worker annotation and parameter metadata.
 **/
@JvmName("workerMetadata")
fun legacyWorkerMetadata(method: Method): Method? = method.workerMetadata()

/**
 * Finds the worker annotation and parameter metadata in the context of the target class.
 **/
@JvmName("workerMetadata")
fun legacyWorkerMetadata(method: Method, targetClass: Class<*>): Method? = method.workerMetadata(targetClass)
