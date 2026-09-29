package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker.Companion.DEFAULT_UNSET_TOPIC
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker.Completion
import dev.bpmcrafters.processengine.worker.Variable
import dev.bpmcrafters.processengine.worker.Variable.Companion.DEFAULT_UNNAMED_NAME
import dev.bpmcrafters.processengine.worker.transaction.TransactionalMethodDetector
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskInformation
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.WildcardType
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Local cache from converter class to instance.
 */
private val converters = ConcurrentHashMap<KClass<out VariableConverter>, VariableConverter>()

/**
 * Names of marker interfaces of generated subclasses / proxies of known CDI / AOP containers.
 */
private val GENERATED_SUBCLASS_MARKERS = setOf(
  "io.quarkus.arc.Subclass",
  "io.quarkus.arc.ClientProxy",
  "org.springframework.aop.SpringProxy",
)

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
 * Retrieves the variable annotation of the parameter.
 * @return annotation or null, if the parameter is not annotated.
 * @since 0.8.6
 */
fun Parameter.getVariableAnnotation(): Variable? = this.getAnnotation(Variable::class.java)

/**
 * Extracts variable name from the variable annotation of the parameter.
 * The attributes `name` and `value` are aliases, if none is set, the parameter name is used.
 */
fun Parameter.extractVariableName(): String {
  val variableAnnotation = requireNotNull(getVariableAnnotation()) { "Parameter ${this.name} is not annotated with @Variable." }
  return resolveAlias(
    first = variableAnnotation.name,
    second = variableAnnotation.value,
    unset = DEFAULT_UNNAMED_NAME,
    description = "@Variable on parameter '${this.name}'",
    firstName = "name",
    secondName = "value"
  ) ?: this.name
}

/**
 * Extracts variable converter for the variable if the parameter is annotated.
 * The converter class must provide a public no-arg constructor (or be a Kotlin object).
 * @param defaultVariableConverter default converter, if none is specified.
 * @return converter for variable.
 */
fun Parameter.extractVariableConverter(defaultVariableConverter: VariableConverter): VariableConverter {
  val variableAnnotation = requireNotNull(getVariableAnnotation()) { "Parameter ${this.name} is not annotated with @Variable." }
  return if (Variable.DefaultVariableConverter::class != variableAnnotation.converter) {
    converters.getOrPut(variableAnnotation.converter) {
      variableAnnotation.converter.java.instantiateConverter()
    }
  } else {
    defaultVariableConverter
  }
}

/*
 * Instantiates a converter using a no-arg constructor, or using the INSTANCE of a Kotlin object.
 */
private fun Class<out VariableConverter>.instantiateConverter(): VariableConverter {
  val instanceField = this.declaredFields.firstOrNull { it.name == "INSTANCE" && java.lang.reflect.Modifier.isStatic(it.modifiers) && this.isAssignableFrom(it.type) }
  if (instanceField != null) {
    return instanceField.get(null) as VariableConverter
  }
  val constructor = try {
    this.getDeclaredConstructor()
  } catch (e: NoSuchMethodException) {
    throw IllegalStateException("Variable converter ${this.name} must provide a public no-arg constructor.", e)
  }
  constructor.isAccessible = true
  return constructor.newInstance()
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
 * Retrieves list of worker methods of the given bean.
 * If the bean is an instance of a generated subclass (proxy of a CDI / AOP container), the user class is used instead.
 * Frameworks knowing the target class should prefer [Class.getAnnotatedWorkers].
 */
fun Any.getAnnotatedWorkers(): List<Method> = this.javaClass.unwrapGeneratedSubclass().getAnnotatedWorkers()

/**
 * Retrieves list of worker methods: public methods (including inherited) annotated with [ProcessEngineWorker].
 * @since 0.8.6
 */
fun Class<*>.getAnnotatedWorkers(): List<Method> = this
  .methods
  .filter { m -> m.isAnnotationPresent(ProcessEngineWorker::class.java) }

/**
 * Unwraps generated subclasses (CGLIB / Arc proxies and intercepted subclasses) to the user class.
 * @return user class.
 * @since 0.8.6
 */
fun Class<*>.unwrapGeneratedSubclass(): Class<*> {
  var current: Class<*> = this
  while (current.isGeneratedSubclass() && current.superclass != null && current.superclass != Any::class.java) {
    current = current.superclass
  }
  return current
}

private fun Class<*>.isGeneratedSubclass(): Boolean =
  this.isSynthetic
    || this.name.contains("$$")
    || this.interfaces.any { it.name in GENERATED_SUBCLASS_MARKERS }

/**
 * Retrieves the worker annotation of the method, searching the method itself, the bridged method (if the method is a bridge) and
 * the same method declared in super classes or interfaces.
 * @return annotation or null, if not found.
 * @since 0.8.6
 */
fun Method.getProcessEngineWorkerAnnotation(): ProcessEngineWorker? = this.findMethodAnnotation(ProcessEngineWorker::class.java)

private fun <A : Annotation> Method.findMethodAnnotation(annotationType: Class<A>): A? {
  this.getAnnotation(annotationType)?.let { return it }
  if (this.isBridge) {
    this.declaringClass.declaredMethods
      .firstOrNull { it.name == this.name && !it.isBridge && it.parameterCount == this.parameterCount && this.returnType.isAssignableFrom(it.returnType) }
      ?.getAnnotation(annotationType)
      ?.let { return it }
  }
  return this.declaringClass.supertypes().firstNotNullOfOrNull { superType ->
    try {
      superType.getDeclaredMethod(this.name, *this.parameterTypes).findMethodAnnotation(annotationType)
    } catch (e: NoSuchMethodException) {
      null
    }
  }
}

private fun Class<*>.supertypes(): List<Class<*>> = buildList {
  superclass?.let { if (it != Any::class.java) add(it) }
  addAll(interfaces)
}

private fun Method.requireWorkerAnnotation(): ProcessEngineWorker =
  requireNotNull(getProcessEngineWorkerAnnotation()) { "Method ${this.declaringClass.name}#${this.name} is not annotated with @ProcessEngineWorker." }

/*
 * Resolves two aliased annotation attributes.
 * @return the value set or null, if none is set.
 * @throws IllegalStateException if both are set to different values.
 */
private fun resolveAlias(first: String, second: String, unset: String, description: String, firstName: String, secondName: String): String? {
  val firstSet = first != unset
  val secondSet = second != unset
  return when {
    firstSet && secondSet && first != second -> throw IllegalStateException(
      "Attributes '$firstName' and '$secondName' of $description are aliases and must not be set to different values, but were '$first' and '$second'."
    )

    firstSet -> first
    secondSet -> second
    else -> null
  }
}

/**
 * Detects worker topic either using the annotation (`topic` or its alias `value`) or defaulting to method name.
 */
fun Method.getTopic(): String {
  val workerAnnotation = requireWorkerAnnotation()
  return resolveAlias(
    first = workerAnnotation.topic,
    second = workerAnnotation.value,
    unset = DEFAULT_UNSET_TOPIC,
    description = "@ProcessEngineWorker on ${this.declaringClass.simpleName}#${this.name}",
    firstName = "topic",
    secondName = "value"
  ) ?: this.name
}

/**
 * Returns the auto-completion flag from annotation.
 */
fun Method.getAutoComplete(): Boolean {
  return requireWorkerAnnotation().autoComplete
}

/**
 * Returns the auto-completion flag from annotation.
 */
fun Method.getCompletion(): Completion {
  return requireWorkerAnnotation().completion
}

/**
 * Returns the lock duration from annotation, or null if not set.
 * @return lock duration in milliseconds, or null if the default should be used.
 * @since 0.8.0
 */
fun Method.getLockDuration(): Long? {
  val lockDuration = requireWorkerAnnotation().lockDuration
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
  val tenantId = requireWorkerAnnotation().tenantId
  return tenantId.ifBlank {
    null
  }
}

/**
 * Checks if the method of the worker is transactional, using the [TransactionalMethodDetector.DEFAULT] detector
 * (Jakarta and Spring transactional annotations on the method or on the declaring class).
 * @return true, if the method should be executed transactional and be atomic with completion of the worker.
 */
fun Method.isTransactional() = TransactionalMethodDetector.DEFAULT.isTransactional(this)
