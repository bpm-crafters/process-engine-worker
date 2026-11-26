package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.registrar.ParameterResolver.Companion.toVariableList
import java.lang.reflect.Parameter
import java.util.*


/**
 * Provides task information.
 * @since 0.6.0
 **/
class TaskInformationParameterResolutionStrategy : ParameterResolutionStrategy {
  /**
   * Checks whether the parameter accepts task information.
   **/
  override fun test(t: Parameter): Boolean = t.isTaskInformation()

  /**
   * Returns the current task information.
   **/
  override fun apply(t: ParameterResolutionStrategy.Wrapper): Any = t.taskInformation
}

/**
 * Provides Service Task Completion API.
 * @since 0.6.0
 **/
class ServiceTaskCompletionApiParameterResolutionStrategy : ParameterResolutionStrategy {
  /**
   * Checks whether the parameter accepts a service-task completion API.
   **/
  override fun test(t: Parameter): Boolean = t.isTaskCompletionApiParameter()

  /**
   * Returns the configured service-task completion API.
   **/
  override fun apply(t: ParameterResolutionStrategy.Wrapper): Any = t.taskCompletionApi
}

/**
 * Provides Payload map.
 * @since 0.6.0
 **/
class PayloadParameterResolutionStrategy : ParameterResolutionStrategy {
  /**
   * Checks whether the parameter accepts the process payload map.
   **/
  override fun test(t: Parameter): Boolean = t.isPayload()

  /**
   * Returns the process payload.
   **/
  override fun apply(t: ParameterResolutionStrategy.Wrapper): Any = t.payload
}

/**
 * Provides globally configured Variable Converter.
 * @since 0.6.0
 **/
class VariableConverterParameterResolutionStrategy : ParameterResolutionStrategy {
  /**
   * Checks whether the parameter accepts a variable converter.
   **/
  override fun test(t: Parameter): Boolean = t.isVariableConverter()
  /**
   * Returns the default variable converter.
   **/
  override fun apply(t: ParameterResolutionStrategy.Wrapper): Any = t.variableConverter
}

/**
 * Provides a single typed variable.
 * @since 0.6.0
 **/
class VariableParameterResolutionStrategy : ParameterResolutionStrategy {
  /**
   * Checks whether the parameter accepts a variable annotation.
   **/
  override fun test(t: Parameter): Boolean = t.isVariable()

  /**
   * Resolves the annotated variable using its converter and required flag.
   **/
  override fun apply(t: ParameterResolutionStrategy.Wrapper): Any? {
    return t.annotationParameter.extractVariableName().let { variablesName ->
      if (t.annotationParameter.extractVariableMandatoryFlag()
        && !t.parameter.isOptional()
      ) { // optional allows null
        require(t.payload.keys.contains(variablesName)) {
          "Expected payload to contain variable '$variablesName', but it contained ${t.payload.toVariableList()}."
        }
      }
      val value = t.payload[variablesName]?.let {
        t.annotationParameter.extractVariableConverter(t.variableConverter).mapToType(
          value = t.payload[variablesName], type = t.parameter.type
        )
      }
      if (t.parameter.isOptional()) {
        Optional.ofNullable(value)
      } else {
        value
      }
    }
  }
}
