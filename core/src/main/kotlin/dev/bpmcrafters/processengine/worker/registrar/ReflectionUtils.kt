package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker.Companion.DEFAULT_UNSET_TOPIC
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker.Completion
import dev.bpmcrafters.processengine.worker.Variable
import dev.bpmcrafters.processengine.worker.Variable.Companion.DEFAULT_UNNAMED_NAME
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskInformation
import java.lang.reflect.Method
import java.lang.reflect.GenericArrayType
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Local cache from converter class to instance.
 */
private val converters = ConcurrentHashMap<KClass<out VariableConverter>, VariableConverter>()

/**
 * Checks if the method parameter is payload of type Map<String, Any> or compatible.
 */
fun Parameter.isPayload() = Map::class.java.isAssignableFrom(this.type)
  && (this.parameterizedType as ParameterizedType).isMapOfStringObject()

/**
 * Checks if the parameter is task information.
 */
fun Parameter.isTaskInformation() = TaskInformation::class.java.isAssignableFrom(this.type)

/**
 * Checks if the parameter is variable converter.
 */
fun Parameter.isVariableConverter() = VariableConverter::class.java.isAssignableFrom(this.type)

/**
 * Checks if parameter is ExternalTaskCompletionApi
 */
fun Parameter.isTaskCompletionApiParameter() = ServiceTaskCompletionApi::class.java.isAssignableFrom(this.type)

/**
 * Checks if the parameter is annotated with a Variable annotation.
 */
fun Parameter.isVariable() = this.isAnnotationPresent(Variable::class.java)

/**
 * Extracts variable name from the variable annotation of the parameter.
 */
fun Parameter.extractVariableName(): String {
  val variableAnnotation = getAnnotation(Variable::class.java)!!
  return resolveAliases(variableAnnotation.name, variableAnnotation.value, DEFAULT_UNNAMED_NAME, name)
}

/**
 * Extracts variable converter for the variable if the parameter is annotated.
 * @param defaultVariableConverter default converter, if none is specified.
 * @return converter for variable.
 */
fun Parameter.extractVariableConverter(defaultVariableConverter: VariableConverter): VariableConverter {
  val variableAnnotation = getAnnotation(Variable::class.java)!!
  return if (Variable.DefaultVariableConverter::class != variableAnnotation.converter) {
    converters.getOrPut(variableAnnotation.converter) {
      variableAnnotation.converter.java.getDeclaredConstructor().newInstance()
    }
  } else {
    defaultVariableConverter
  }
}

/**
 * Extracts variable mandatory flag.
 */
fun Parameter.extractVariableMandatoryFlag() = this.getAnnotation(Variable::class.java).mandatory

/**
 * Checks if parameter is an Optional
 */
fun Parameter.isOptional() = Optional::class.java.isAssignableFrom(this.type)


/**
 * Extract variable names from all parameters.
 */
fun List<Parameter>.extractVariableNames(): Set<String> = this.map { it.extractVariableName() }.toSet()

/**
 * Checks if the method has a return type compatible with payload of type Map<String, Any>
 */
fun Method.hasPayloadReturnType() =
  Map::class.java.isAssignableFrom(this.returnType) // try if the type is compatible to Map<String, Object>
    && if (this.genericReturnType is ParameterizedType) {
    // e.g. Map<String, String>
    (this.genericReturnType as ParameterizedType).isMapOfStringObject()
  } else {
    // e.g. class VariableMap implements Map<String, Object> (one of the interfaces are parameterized type matching the Map<String, Any>)
    (this.genericReturnType as Class<*>).genericInterfaces.filterIsInstance<ParameterizedType>()
      .any { it.isMapOfStringObject() }
  }

/*
 * Checks a two-types parameter type for the type bounds.
 * Verifies TYPE<*, *> to be compatible to TYPE<String, out Any>.
 */
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
 * Checks if the return type is void.
 */
fun Method.hasVoidReturnType() = Void.TYPE == this.returnType


/**
 * Retrieves list of worker methods.
 */
fun Any.getAnnotatedWorkers(): List<Method> = javaClass.methods
  .filter { !it.isBridge && !it.isSynthetic && it.workerMetadata() != null }

/** Finds direct or interface worker metadata without depending on a framework annotation model. */
fun Method.workerMetadata(): Method? {
  if (isAnnotationPresent(ProcessEngineWorker::class.java)) return this
  val inheritedTypes = declaringClass.interfaces.toList() + listOfNotNull(declaringClass.superclass)
  val inherited = inheritedTypes.firstNotNullOfOrNull { type ->
    runCatching { type.getMethod(name, *parameterTypes) }.getOrNull()?.workerMetadata()
  }
  if (inherited != null) return inherited
  return declaringClass.genericParents().firstNotNullOfOrNull { genericWorkerMetadata(it, emptyMap()) }
}

/** Resolves contracts introduced by the actual bean, even when its implementation is inherited. */
fun Method.workerMetadata(beanClass: Class<*>): Method? = workerMetadata()
  ?: beanClass.genericParents().firstNotNullOfOrNull { genericWorkerMetadata(it, emptyMap()) }

/** Matches resolved generic parameters, rather than attributing an erased bridge to unrelated overloads. */
private fun Method.genericWorkerMetadata(type: Type, bindings: Map<TypeVariable<*>, Type>): Method? {
  val owner = type.rawClass() ?: return null
  val resolved = bindings + if (type is ParameterizedType) {
    owner.typeParameters.zip(type.actualTypeArguments.map { bindings[it] ?: it }).toMap()
  } else {
    emptyMap()
  }
  return owner.declaredMethods.firstOrNull { candidate ->
    !candidate.isBridge && candidate.name == name && candidate.parameterCount == parameterCount
      && candidate.isAnnotationPresent(ProcessEngineWorker::class.java)
      && candidate.genericParameterTypes.map { (resolved[it] ?: it).rawClass(resolved) } == parameterTypes.toList()
  } ?: owner.genericParents().firstNotNullOfOrNull { genericWorkerMetadata(it, resolved) }
}

private fun Class<*>.genericParents(): List<Type> = genericInterfaces.toList() + listOfNotNull(genericSuperclass)

private fun Type.rawClass(bindings: Map<TypeVariable<*>, Type> = emptyMap()): Class<*>? = when (this) {
  is Class<*> -> this
  is ParameterizedType -> rawType as Class<*>
  is GenericArrayType -> (bindings[genericComponentType] ?: genericComponentType).rawClass(bindings)?.let {
    java.lang.reflect.Array.newInstance(it, 0).javaClass
  }
  else -> null
}

private fun resolveAliases(first: String, second: String, unset: String, fallback: String): String {
  require(first == unset || second == unset || first == second) {
    "Conflicting annotation aliases: '$first' and '$second'"
  }
  return first.takeUnless { it == unset } ?: second.takeUnless { it == unset } ?: fallback
}

/**
 * Detects worker topic either using the annotation or defaulting to method name.
 */
fun Method.getTopic(): String {
  val workerAnnotation = workerAnnotation()
  return resolveAliases(workerAnnotation.topic, workerAnnotation.value, DEFAULT_UNSET_TOPIC, name)
}

/**
 * Returns the auto-completion flag from annotation.
 */
fun Method.getAutoComplete(): Boolean {
  return workerAnnotation().autoComplete
}

/**
 * Returns the auto-completion flag from annotation.
 */
fun Method.getCompletion(): Completion {
  return workerAnnotation().completion
}

/**
 * Returns the lock duration from annotation, or null if not set.
 * @return lock duration in milliseconds, or null if the default should be used.
 * @since 0.8.0
 */
fun Method.getLockDuration(): Long? {
  val lockDuration = workerAnnotation().lockDuration
  return if (lockDuration == ProcessEngineWorker.DEFAULT_UNSET_LOCK_DURATION) {
    null
  } else {
    lockDuration
  }
}

/**
 * Returns the tenant id, configured on the annotation or null if it is not set.
 * @return tenant id, or null if the default should be used.
 * @since 0.8.4
 */
fun Method.getTenantId(): String? {
  val tenantId = workerAnnotation().tenantId
  return tenantId.ifBlank {
    null
  }
}

private fun Method.workerAnnotation(): ProcessEngineWorker = requireNotNull(workerMetadata()) {
  "No @ProcessEngineWorker metadata found for $this"
}.getAnnotation(ProcessEngineWorker::class.java)
