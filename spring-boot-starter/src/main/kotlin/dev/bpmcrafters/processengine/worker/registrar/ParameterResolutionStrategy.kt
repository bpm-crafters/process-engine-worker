package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskInformation
import java.lang.reflect.Parameter
import java.util.function.Function
import java.util.function.Predicate

/**
 * Parameter resolution strategy.
 * @since 0.0.1
 */
interface ParameterResolutionStrategy : Predicate<Parameter>, Function<ParameterResolutionStrategy.Wrapper, Any?> {

  /**
   * Wraps parameters of parameter and services for determination of the matching resolution strategy.
   */
  data class Wrapper(
    val parameter: Parameter,
    val taskInformation: TaskInformation,
    val payload: Map<String, Any>,
    val variableConverter: VariableConverter,
    val taskCompletionApi: ServiceTaskCompletionApi
  )

}
