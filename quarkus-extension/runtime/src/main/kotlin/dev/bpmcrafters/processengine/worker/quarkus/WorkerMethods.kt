package dev.bpmcrafters.processengine.worker.quarkus

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.registrar.workerMetadata
import java.lang.reflect.*

/** Shared discovery rules; concrete signatures remain separate from inherited annotations. */
object WorkerMethods {
  @JvmStatic
  fun discover(beanClass: Class<*>): List<Method> {
    validateDeclaredMethods(beanClass)
    return beanClass.methods.filter { !it.isBridge && !it.isSynthetic && it.workerMetadata(beanClass) != null }
      .onEach { validate(it); validateConcreteSignature(it) }
  }

  private fun validateConcreteSignature(method: Method) {
    require(!method.genericParameterTypes.any(::hasTypeVariable) && !hasTypeVariable(method.genericReturnType)) {
      "Worker requires a concrete signature; override inherited generic methods with concrete types: $method"
    }
  }

  private fun hasTypeVariable(type: Type): Boolean = when (type) {
    is TypeVariable<*> -> true
    is ParameterizedType -> type.actualTypeArguments.any(::hasTypeVariable)
    is GenericArrayType -> hasTypeVariable(type.genericComponentType)
    is WildcardType -> type.upperBounds.any(::hasTypeVariable) || type.lowerBounds.any(::hasTypeVariable)
    else -> false
  }

  private fun validateDeclaredMethods(type: Class<*>?) {
    if (type == null || type == Any::class.java) return
    type.declaredMethods.filter { it.isAnnotationPresent(ProcessEngineWorker::class.java) }.forEach(::validate)
    validateDeclaredMethods(type.superclass)
    type.interfaces.forEach(::validateDeclaredMethods)
  }

  private fun validate(method: Method) {
    require(Modifier.isPublic(method.modifiers) && !Modifier.isStatic(method.modifiers)) {
      "Worker must be a public instance method: $method"
    }
  }

  @JvmStatic
  fun descriptor(beanClass: Class<*>, method: Method): String =
    "${beanClass.name}|${method.name}|${method.parameterTypes.joinToString(",") { it.name }}"

  @JvmStatic
  fun resolve(beanClass: Class<*>, descriptor: String): Method =
    discover(beanClass).find { descriptor(beanClass, it) == descriptor }
      ?: throw IllegalArgumentException("Worker method not found: $descriptor")
}
