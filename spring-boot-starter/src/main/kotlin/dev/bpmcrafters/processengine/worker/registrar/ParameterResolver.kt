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
    fun builder() = ParameterResolverBuilder()

    /**
     * Builder for convenient instantiation of parameter resolver.
     */
    class ParameterResolverBuilder(
      private val strategies: MutableList<ParameterResolutionStrategy> = mutableListOf()
    ) {

      init {
        this.strategies.addAll(
          listOf(
            // TaskInformation
            ParameterResolutionStrategy(
              parameterMatcher = { param -> param.isTaskInformation() },
              parameterExtractor = { parameterExtractor -> parameterExtractor.taskInformation },
            ),
            // ServiceTaskCompletionApi
            ParameterResolutionStrategy(
              parameterMatcher = { param -> param.isTaskCompletionApiParameter() },
              parameterExtractor = { parameterExtractor -> parameterExtractor.taskCompletionApi },
            ),
            // Payload: Map<String, Any>
            ParameterResolutionStrategy(
              parameterMatcher = { param -> param.isPayload() },
              parameterExtractor = { parameterExtractor -> parameterExtractor.payload },
            ),
            // Variable converter
            ParameterResolutionStrategy(
              parameterMatcher = { param -> param.isVariableConverter() },
              parameterExtractor = { parameterExtractor -> parameterExtractor.variableConverter },
            ),
            // Annotated variable (`@Variable`)
            ParameterResolutionStrategy(
              parameterMatcher = { param -> param.isVariable() },
              parameterExtractor = { parameter, _, payload, variableConverter, _ ->
                parameter.extractVariableName().let { variablesName ->
                  if (parameter.extractVariableMandatoryFlag() && !parameter.isOptional()) { // optional allows null
                    require(payload.keys.contains(variablesName)) {
                      "Expected payload to contain variable '$variablesName', but it contained ${payload.toVariableList()}."
                    }
                  }
                  val value = payload[variablesName]?.let {
                    variableConverter.mapToType(value = payload[variablesName], type = parameter.type)
                  }
                  if (parameter.isOptional()) {
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
      parameterMatcher: Predicate<Parameter>,
      parameterExtractor: Function<ParameterExtractor, Any?>
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
      }
    )
  }

  /**
   * At the end, the parameter resolver is getting an annotated worker method and is responsible for creation a list of arguments to be passed to the
   * invocation on the bean.
   * @param method annotated worker method.
   * @param taskInformation task information.
   * @param payload payload.
   * @param taskCompletionApi completion api.
   * @param variableConverter converter from JSON to variable map.
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
      (this.strategies.firstOrNull { it.parameterMatcher.test(parameter) }
        ?: throw IllegalArgumentException(
          "Found a method with some unsupported parameters annotated with `@ProcessEngineWorker`. "
            + "Could not find a strategy to resolve argument $i of ${method.declaringClass.simpleName}#${method.name} of type ${parameter.type.simpleName}."
        )
      ).parameterExtractor.invoke(parameter, taskInformation, payload, variableConverter, taskCompletionApi)
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
