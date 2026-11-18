package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskInformation
import java.lang.reflect.Method
import java.lang.reflect.Parameter

/**
 * A parameter resolver is responsible for resolution of the parameters of the worker method.
 * @since 0.0.3
 */
open class ParameterResolver private constructor(
  private val strategies: List<ParameterResolutionStrategy>
) {
  companion object {

    fun Map<String, Any?>.toVariableList(): String {
      return if (this.isEmpty()) {
        "no process variables"
      } else {
        this.keys.joinToString(", ") { chars -> "'$chars'" }
      }
    }

    /**
     * Creates a new builder responsible for creation of the parameter resolver.
     */
    @JvmStatic
    fun builder() = ParameterResolverBuilder(mutableListOf())

    /**
     * Creates a new builder with predefined strategies.
     */
    @JvmStatic
    fun builder(strategies: List<ParameterResolutionStrategy>) = ParameterResolverBuilder(strategies.toMutableList())

    /**
     * Builder for convenient instantiation of parameter resolver.
     */
    class ParameterResolverBuilder(
      private val strategies: MutableList<ParameterResolutionStrategy>
    ) {

      private fun addDefaultStrategies() {
        this.strategies.addAll(
          listOf(
            TaskInformationParameterResolutionStrategy(),
            ServiceTaskCompletionApiParameterResolutionStrategy(),
            PayloadParameterResolutionStrategy(),
            VariableConverterParameterResolutionStrategy(),
            VariableParameterResolutionStrategy()
          )
        )
      }

      /**
       * Adds a new strategy.
       * @param strategy strategy to add.
       * @return builder instance.
       */
      fun addStrategy(strategy: ParameterResolutionStrategy): ParameterResolverBuilder {
        this.strategies.add(strategy)
        return this
      }

      /**
       * Creates the resolver.
       * @return resolver instance.
       */
      fun build(): ParameterResolver {
        addDefaultStrategies()
        return ParameterResolver(
          strategies = this.strategies
        )
      }
    }
  }

  /**
   * The parameter resolver is getting an annotated worker method and is responsible for
   * creation a list of arguments to be passed to the invocation on the bean method, using the
   * specified parameter resolution strategies.
   *
   * @param method annotated worker method.
   * @param taskInformation task information.
   * @param payload payload.
   * @param taskCompletionApi completion api.
   * @param variableConverter default converter for the variables.
   * @return arguments list.
   */
  open fun createInvocationArguments(
    method: Method,
    taskInformation: TaskInformation,
    payload: Map<String, Any?>,
    variableConverter: VariableConverter,
    taskCompletionApi: ServiceTaskCompletionApi
  ): Array<Any?> = resolveArguments(method, method, taskInformation, payload, variableConverter, taskCompletionApi)

  /** Resolves using concrete types while retaining interface variable annotations. */
  open fun createInvocationArguments(
    method: Method,
    metadataMethod: Method,
    taskInformation: TaskInformation,
    payload: Map<String, Any?>,
    variableConverter: VariableConverter,
    taskCompletionApi: ServiceTaskCompletionApi
  ): Array<Any?> = if (method == metadataMethod) {
    createInvocationArguments(method, taskInformation, payload, variableConverter, taskCompletionApi)
  } else {
    resolveArguments(method, metadataMethod, taskInformation, payload, variableConverter, taskCompletionApi)
  }

  private fun resolveArguments(
    method: Method,
    metadataMethod: Method,
    taskInformation: TaskInformation,
    payload: Map<String, Any?>,
    variableConverter: VariableConverter,
    taskCompletionApi: ServiceTaskCompletionApi
  ): Array<Any?> {
    val arguments = method.parameters.mapIndexed { i: Int, parameter: Parameter ->
      val annotationParameter = if (parameter.isVariable()) parameter else metadataMethod.parameters[i]
      val matchingParameter = if (annotationParameter.isVariable()) annotationParameter else parameter
      val matchingStrategies = this.strategies.filter { it.test(matchingParameter) }
      require(matchingStrategies.isNotEmpty()) {
        "Found a method with some unsupported parameters annotated with `@ProcessEngineWorker`. " +
          "Could not find a strategy to resolve argument $i of ${method.declaringClass.simpleName}#${method.name} " +
          "of type ${parameter.type.simpleName}."
      }
      require(matchingStrategies.size == 1) {
        "Found a method with some unsupported parameters annotated with `@ProcessEngineWorker`. " +
          "Expected exactly one strategy for parameter type ${parameter.type.simpleName}, " +
          "but detected ${
            matchingStrategies.joinToString(
              ", "
            ) { it.javaClass.simpleName }
          }"
      }
      matchingStrategies.single().apply(
        ParameterResolutionStrategy.Wrapper(
          parameter,
          taskInformation,
          payload,
          variableConverter,
          taskCompletionApi,
          annotationParameter
        )
      )
    }

    return arguments.toTypedArray()
  }

}
