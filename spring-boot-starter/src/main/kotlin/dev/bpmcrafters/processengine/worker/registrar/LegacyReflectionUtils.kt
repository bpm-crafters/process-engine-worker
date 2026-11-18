@file:JvmName("ReflectionUtilsKt")

package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker.Completion
import org.springframework.aop.support.AopUtils
import org.springframework.core.BridgeMethodResolver
import java.lang.reflect.Method
import java.lang.reflect.Parameter

// Keep the original static JVM owner and descriptors for precompiled starter clients and Java imports.
// Most helpers have distinct Kotlin names; discovery retains the original proxy-aware extension.
@JvmName("isPayload")
fun legacyIsPayload(parameter: Parameter): Boolean = parameter.isPayload()

@JvmName("isTaskInformation")
fun legacyIsTaskInformation(parameter: Parameter): Boolean = parameter.isTaskInformation()

@JvmName("isVariableConverter")
fun legacyIsVariableConverter(parameter: Parameter): Boolean = parameter.isVariableConverter()

@JvmName("isTaskCompletionApiParameter")
fun legacyIsTaskCompletionApiParameter(parameter: Parameter): Boolean = parameter.isTaskCompletionApiParameter()

@JvmName("isVariable")
fun legacyIsVariable(parameter: Parameter): Boolean = parameter.isVariable()

@JvmName("extractVariableName")
fun legacyExtractVariableName(parameter: Parameter): String = parameter.extractVariableName()

@JvmName("extractVariableConverter")
fun legacyExtractVariableConverter(parameter: Parameter, defaultVariableConverter: VariableConverter): VariableConverter =
  parameter.extractVariableConverter(defaultVariableConverter)

@JvmName("extractVariableMandatoryFlag")
fun legacyExtractVariableMandatoryFlag(parameter: Parameter): Boolean = parameter.extractVariableMandatoryFlag()

@JvmName("isOptional")
fun legacyIsOptional(parameter: Parameter): Boolean = parameter.isOptional()

@JvmName("extractVariableNames")
fun legacyExtractVariableNames(parameters: List<Parameter>): Set<String> = parameters.extractVariableNames()

@JvmName("hasPayloadReturnType")
fun legacyHasPayloadReturnType(method: Method): Boolean = method.hasPayloadReturnType()

@JvmName("hasVoidReturnType")
fun legacyHasVoidReturnType(method: Method): Boolean = method.hasVoidReturnType()

/** Keeps original Kotlin calls and callable references proxy-aware. */
fun Any.getAnnotatedWorkers(): List<Method> {
  val targetClass = AopUtils.getTargetClass(this)
  return targetClass.methods.map { BridgeMethodResolver.findBridgedMethod(it) }.distinct()
    .filter { !it.isSynthetic && it.workerMetadata(targetClass) != null }
}

/** Source alias for callers using the explicitly named legacy helper. */
fun legacyGetAnnotatedWorkers(worker: Any): List<Method> = worker.getAnnotatedWorkers()

@JvmName("getTopic")
fun legacyGetTopic(method: Method): String = method.getTopic()

@JvmName("getAutoComplete")
fun legacyGetAutoComplete(method: Method): Boolean = method.getAutoComplete()

@JvmName("getCompletion")
fun legacyGetCompletion(method: Method): Completion = method.getCompletion()

@JvmName("getLockDuration")
fun legacyGetLockDuration(method: Method): Long? = method.getLockDuration()

@JvmName("getTenantId")
fun legacyGetTenantId(method: Method): String? = method.getTenantId()

@JvmName("isTransactional")
fun legacyIsTransactional(method: Method): Boolean = method.isTransactional()

@JvmName("workerMetadata")
fun legacyWorkerMetadata(method: Method): Method? = method.workerMetadata()

@JvmName("workerMetadata")
fun legacyWorkerMetadata(method: Method, targetClass: Class<*>): Method? = method.workerMetadata(targetClass)
