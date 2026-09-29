package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.registrar.ParameterResolver.Companion.toVariableList
import java.lang.reflect.Parameter
import java.util.*


/**
 * Provides task information.
 */
class TaskInformationParameterResolutionStrategy : ParameterResolutionStrategy {
  override fun test(t: Parameter): Boolean = t.isTaskInformation()

  override fun apply(t: ParameterResolutionStrategy.Wrapper): Any = t.taskInformation
}

/**
 * Provides Service Task Completion API.
 */
class ServiceTaskCompletionApiParameterResolutionStrategy : ParameterResolutionStrategy {
  override fun test(t: Parameter): Boolean = t.isTaskCompletionApiParameter()

  override fun apply(t: ParameterResolutionStrategy.Wrapper): Any = t.taskCompletionApi
}

/**
 * Provides Payload map.
 */
class PayloadParameterResolutionStrategy : ParameterResolutionStrategy {
  override fun test(t: Parameter): Boolean = t.isPayload()

  override fun apply(t: ParameterResolutionStrategy.Wrapper): Any = t.payload
}

/**
 * Provides globally configured Variable Converter.
 */
class VariableConverterParameterResolutionStrategy : ParameterResolutionStrategy {
  override fun test(t: Parameter): Boolean = t.isVariableConverter()
  override fun apply(t: ParameterResolutionStrategy.Wrapper): Any = t.variableConverter
}

/**
 * Provides a single typed variable.
 */
class VariableParameterResolutionStrategy : ParameterResolutionStrategy {
  override fun test(t: Parameter): Boolean = t.isVariable()

  override fun apply(t: ParameterResolutionStrategy.Wrapper): Any? {
    return t.parameter.extractVariableName().let { variablesName ->
      if (t.parameter.extractVariableMandatoryFlag()
        && !t.parameter.isOptional()
      ) { // optional allows null
        require(t.payload.keys.contains(variablesName)) {
          "Expected payload to contain variable '$variablesName', but it contained ${t.payload.toVariableList()}."
        }
      }
      val value = t.payload[variablesName]?.let {
        t.parameter.extractVariableConverter(t.variableConverter).mapToType(
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
