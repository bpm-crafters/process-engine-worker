package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskInformation
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.util.*
import java.util.function.Function
import java.util.function.Predicate

/**
 * A parameter resolver is responsible for resolution of the parameters of the worker method.
 * @since 0.0.3
 */
open class ParameterResolver private constructor(
  private val strategies: List<ParameterResolutionStrategy>
) {
  companion object {

    fun Map<String, Any>.toVariableList(): String {
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
            // TaskInformation
            ParameterResolutionStrategy(
              parameterMatcher = { param -> param.isTaskInformation() },
              parameterExtractor = { pe -> pe.taskInformation },
            ),
            // ServiceTaskCompletionApi
            ParameterResolutionStrategy(
              parameterMatcher = { param -> param.isTaskCompletionApiParameter() },
              parameterExtractor = { pe -> pe.taskCompletionApi },
            ),
            // Payload: Map<String, Any>
            ParameterResolutionStrategy(
              parameterMatcher = { param -> param.isPayload() },
              parameterExtractor = { pe -> pe.payload },
            ),
            // Variable converter
            ParameterResolutionStrategy(
              parameterMatcher = { param -> param.isVariableConverter() },
              parameterExtractor = { pe -> pe.variableConverter },
            ),
            // Annotated variable (`@Variable`)
            ParameterResolutionStrategy(
              parameterMatcher = { param -> param.isVariable() },
              parameterExtractor = { pe ->
                pe.parameter.extractVariableName().let { variablesName ->
                  if (pe.parameter.extractVariableMandatoryFlag()
                    && !pe.parameter.isOptional()
                  ) { // optional allows null
                    require(pe.payload.keys.contains(variablesName)) {
                      "Expected payload to contain variable '$variablesName', but it contained ${pe.payload.toVariableList()}."
                    }
                  }
                  val value = pe.payload[variablesName]?.let {
                    pe.variableConverter.mapToType(
                      value = pe.payload[variablesName], type = pe.parameter.type
                    )
                  }
                  if (pe.parameter.isOptional()) {
                    Optional.ofNullable(value)
                  } else {
                    value
                  }
                }
              }
            )
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
   * Parameter resolution strategy.
   * @param parameterMatcher matcher to be applied to parameter to decide if the strategy can be applied to the given parameter.
   * @param parameterExtractor extractor for the invocation argument. Maybe null, denoting that the extraction is not possible.
   */
  data class ParameterResolutionStrategy(
    val parameterMatcher: Predicate<Parameter>,
    val parameterExtractor: (Parameter, TaskInformation, Map<String, Any>, VariableConverter, ServiceTaskCompletionApi) -> Any?
  ) {

    /**
     * Java friendly secondary constructor that makes it easier to implement
     * the parameterExtractor function.
     */
    constructor(
      parameterMatcher: Predicate<Parameter>, parameterExtractor: Function<ParameterExtractor, Any?>
    ) : this(
      parameterMatcher = parameterMatcher,
      parameterExtractor = { parameter, taskInformation, payload, variableConverter, taskCompletionApi ->
        parameterExtractor.apply(
          ParameterExtractor(
            parameter = parameter,
            taskInformation = taskInformation,
            payload = payload,
            variableConverter = variableConverter,
            taskCompletionApi = taskCompletionApi
          )
        )
      })
  }

  /**
   * At the end, the parameter resolver is getting an annotated worker method and is responsible for
   * creation a list of arguments to be passed to the invocation on the bean method.
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
    payload: Map<String, Any>,
    variableConverter: VariableConverter,
    taskCompletionApi: ServiceTaskCompletionApi
  ): Array<Any?> {
    val arguments = method.parameters.mapIndexed { i: Int, parameter: Parameter ->

      val matchingStrategies = this.strategies.filter { it.parameterMatcher.test(parameter) }
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
      matchingStrategies.first().parameterExtractor.invoke(
        parameter,
        taskInformation,
        payload,
        if (parameter.isOptional()) {
          parameter.extractVariableConverter(variableConverter)
        } else {
          variableConverter
        },
        taskCompletionApi
      )
    }

    return arguments.toTypedArray()
  }

  /**
   * Wraps parameters of parameterExtractor for simplified usage from java.
   */
  data class ParameterExtractor(
    val parameter: Parameter,
    val taskInformation: TaskInformation,
    val payload: Map<String, Any>,
    val variableConverter: VariableConverter,
    val taskCompletionApi: ServiceTaskCompletionApi
  )
}
