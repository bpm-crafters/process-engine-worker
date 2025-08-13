package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker.Companion.DEFAULT_UNSET_TOPIC
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker.Completion
import dev.bpmcrafters.processengine.worker.Variable
import dev.bpmcrafters.processengine.worker.Variable.Companion.DEFAULT_UNNAMED_NAME
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.springframework.aop.support.AopUtils
import org.springframework.core.annotation.AnnotationUtils
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.WildcardType
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

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
fun Parameter.isVariableConverter() = JacksonVariableConverter::class.java.isAssignableFrom(this.type)

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
    val variableAnnotation = AnnotationUtils.findAnnotation(this, Variable::class.java)!!
    return if (variableAnnotation.name == DEFAULT_UNNAMED_NAME) {
        this.name
    } else {
        variableAnnotation.name
    }
}

/**
 * Extracts variable converter for the variable.
 * @param defaultVariableConverter default converter, if none is specified.
 * @return converter for variable.
 */
fun Parameter.extractVariableConverter(defaultVariableConverter: VariableConverter): VariableConverter {
    if (!isVariable()) {
        return defaultVariableConverter
    }
    val variableAnnotation = AnnotationUtils.findAnnotation(this, Variable::class.java)!!
    return if (NoneVariableConverter::class == variableAnnotation.converter) {
        defaultVariableConverter
    } else {
        converters.getOrPut(variableAnnotation.converter) {
            variableAnnotation.converter.primaryConstructor!!.call()
        }
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
fun Any.getAnnotatedWorkers(): List<Method> = AopUtils
    .getTargetClass(this)
    .methods
    .filter { m -> m.isAnnotationPresent(ProcessEngineWorker::class.java) }

/**
 * Detects worker topic either using the annotation or defaulting to method name.
 */
fun Method.getTopic(): String {
    val workerAnnotation = AnnotationUtils.findAnnotation(this, ProcessEngineWorker::class.java)!!
    return if (workerAnnotation.topic != DEFAULT_UNSET_TOPIC) {
        workerAnnotation.topic
    } else {
        this.name
    }
}

/**
 * Returns the auto-completion flag from annotation.
 */
fun Method.getAutoComplete(): Boolean {
    return this.getAnnotation(ProcessEngineWorker::class.java).autoComplete
}

/**
 * Returns the auto-completion flag from annotation.
 */
fun Method.getCompletion(): Completion {
    return this.getAnnotation(ProcessEngineWorker::class.java).completion
}

/**
 * Checks if the method of the worker is transactional.
 * @return true, if the method should be executed transactional and be atomic with completion of the worker.
 */
fun Method.isTransactional() =
    this.getAnnotation(org.springframework.transaction.annotation.Transactional::class.java)
        ?.isTransactionalRequired() ?: false
            || this.declaringClass.getAnnotation(org.springframework.transaction.annotation.Transactional::class.java)
        ?.isTransactionalRequired() ?: false
            || this.getAnnotation(jakarta.transaction.Transactional::class.java)?.isTransactionRequired() ?: false
            || this.declaringClass.getAnnotation(jakarta.transaction.Transactional::class.java)
        ?.isTransactionRequired() ?: false


/**
 * Resolves Spring TX propagation types.
 */
private fun org.springframework.transaction.annotation.Transactional.isTransactionalRequired() =
    this.propagation == org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
            || this.propagation == org.springframework.transaction.annotation.Propagation.REQUIRED
            || this.propagation == org.springframework.transaction.annotation.Propagation.SUPPORTS
            || this.propagation == org.springframework.transaction.annotation.Propagation.MANDATORY

/**
 * Resolves Jakarta TX propagation types.
 */
private fun jakarta.transaction.Transactional.isTransactionRequired() =
    this.value == jakarta.transaction.Transactional.TxType.REQUIRED
            || this.value == jakarta.transaction.Transactional.TxType.REQUIRES_NEW
            || this.value == jakarta.transaction.Transactional.TxType.SUPPORTS
            || this.value == jakarta.transaction.Transactional.TxType.MANDATORY

