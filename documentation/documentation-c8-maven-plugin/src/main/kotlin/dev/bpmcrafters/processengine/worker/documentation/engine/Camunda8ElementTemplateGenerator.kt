package dev.bpmcrafters.processengine.worker.documentation.engine

import dev.bpmcrafters.processengine.worker.documentation.api.PropertyType
import dev.bpmcrafters.processengine.worker.documentation.core.SchemaJsonConverter
import dev.bpmcrafters.processengine.worker.documentation.engine.schema.Binding
import dev.bpmcrafters.processengine.worker.documentation.engine.schema.CamundaC8ElementTemplate
import dev.bpmcrafters.processengine.worker.documentation.engine.schema.Constraints
import dev.bpmcrafters.processengine.worker.documentation.engine.schema.Property
import dev.bpmcrafters.processengine.worker.documentation.generator.api.*

class Camunda8ElementTemplateGenerator(
  private val inputValueNamingPolicy: InputValueNamingPolicy = InputValueNamingPolicy.EMPTY,
): EngineDocumentationGeneratorApi {

  override fun generate(
    processEngineWorkerDocumentationInfo: ProcessEngineWorkerDocumentationInfo,
  ): GenerationResult {

    val elementTemplate = CamundaC8ElementTemplate()
    elementTemplate.name = processEngineWorkerDocumentationInfo.name
    elementTemplate.id = processEngineWorkerDocumentationInfo.type
    elementTemplate.appliesTo = mutableListOf(BPMNElementType.BPMN_SERVICE_TASK.value)


    if (processEngineWorkerDocumentationInfo.version > 0) {
      elementTemplate.version = processEngineWorkerDocumentationInfo.version
    }

    val implementationTopicProperty = Property()
    implementationTopicProperty.label = "Topic"
    implementationTopicProperty.type = PropertyType.STRING.type
    implementationTopicProperty.editable = false
    val binding = Binding()
    binding.type = Binding.Type.ZEEBE_TASKDEFINITION_TYPE
    implementationTopicProperty.binding = binding

    elementTemplate.properties.add(implementationTopicProperty)

    for (inputProperty in processEngineWorkerDocumentationInfo.inputProperties) {
      val property = createInputParameterProp(inputProperty, inputValueNamingPolicy)
      elementTemplate.properties.add(property)
    }

    for (ouputProperty in processEngineWorkerDocumentationInfo.outputProperties) {
      val property = createOutputParameterProp(ouputProperty, inputValueNamingPolicy)
      elementTemplate.properties.add(property)
    }

    val jsonSchema = "https://unpkg.com/@camunda/element-templates-json-schema@0.1.0/resources/schema.json"
    val json = SchemaJsonConverter.toJsonString(jsonSchema, elementTemplate, CamundaC8ElementTemplate::class.java)

    return GenerationResult(
      name = processEngineWorkerDocumentationInfo.name,
      version = processEngineWorkerDocumentationInfo.version,
      content = json,
      fileName = processEngineWorkerDocumentationInfo.name.replace(" ", "-") + ".json",
      engine = TargetPlattform.C8
    )
  }

  private fun createInputParameterProp(
    info: ProcessEngineWorkerPropertyInfo,
    inputValueNamingPolicy: InputValueNamingPolicy
  ): Property {
    val property = Property()
    property.label = "Input: " + info.label
    property.value = if (InputValueNamingPolicy.ATTRIBUTE_NAME == inputValueNamingPolicy) "=" + info.name else "="
    property.type = info.type.type
    val binding = Binding()
    binding.type = Binding.Type.ZEEBE_INPUT
    binding.name = info.name
    property.binding = binding

    val constraint = Constraints()
    constraint.notEmpty = info.notEmpty
    property.constraints = constraint

    property.editable = info.editable

    return property
  }

    private fun createOutputParameterProp(
      info: ProcessEngineWorkerPropertyInfo,
      inputValueNamingPolicy: InputValueNamingPolicy
    ): Property {
      val property = Property()
      property.label = "Output: " + info.label
      property.value = info.name
      property.type = info.type.type
      val binding = Binding()
      binding.type = Binding.Type.ZEEBE_OUTPUT
      binding.source = "=" + info.name
      property.binding = binding

      val constraint = Constraints()
      constraint.notEmpty = info.notEmpty
      property.constraints = constraint

      property.editable = info.editable

      return property
    }

}
