package dev.bpmcrafters.processengine.worker.documentation.engine

import dev.bpmcrafters.processengine.worker.documentation.C7Config
import dev.bpmcrafters.processengine.worker.documentation.api.PropertyType
import dev.bpmcrafters.processengine.worker.documentation.core.SchemaJsonConverter
import dev.bpmcrafters.processengine.worker.documentation.elementtemplates.gen.Binding
import dev.bpmcrafters.processengine.worker.documentation.elementtemplates.gen.CamundaC7ElementTemplate
import dev.bpmcrafters.processengine.worker.documentation.elementtemplates.gen.Constraints
import dev.bpmcrafters.processengine.worker.documentation.elementtemplates.gen.Property
import dev.bpmcrafters.processengine.worker.documentation.generator.api.*

class Camunda7ElementTemplateGenerator(
  private val inputValueNamingPolicy: InputValueNamingPolicy = InputValueNamingPolicy.EMPTY,
  private val c7Config: C7Config = C7Config()
): EngineDocumentationGeneratorApi {

  override fun generate(processEngineWorkerDocumentationInfo: ProcessEngineWorkerDocumentationInfo): GenerationResult {
    val elementTemplate = CamundaC7ElementTemplate()
      .withName(processEngineWorkerDocumentationInfo.name)
      .withId(processEngineWorkerDocumentationInfo.type)
      .withAppliesTo(mutableListOf(BPMNElementType.BPMN_SERVICE_TASK.value))

    if (processEngineWorkerDocumentationInfo.version > 0) {
      elementTemplate.version = processEngineWorkerDocumentationInfo.version.toDouble()
    }


    // Add external task property
    val implementationProperty = createExternalTaskProperty()
    elementTemplate.properties.add(implementationProperty)


    // Add property for the topic of the external task
    val implementationTopicProperty = createExternalTaskTopicProperty(processEngineWorkerDocumentationInfo.type)
    elementTemplate.properties.add(implementationTopicProperty)


    // Add asyncBefore property
    val asyncBeforeProperty = createAsyncBeforeProperty(c7Config.asyncBeforeDefaultValue)
    elementTemplate.properties.add(asyncBeforeProperty)


    // Add asyncAfter property
    val asyncAfterProperty = createAsyncAfterProperty(c7Config.asyncAfterDefaultValue)
    elementTemplate.properties.add(asyncAfterProperty)


    // Add properties for input parameters
    for (inputProperty in processEngineWorkerDocumentationInfo.inputProperties) {
      val property = createInputParameterProp(inputProperty, inputValueNamingPolicy)
      elementTemplate.properties.add(property)
    }


    // Add properties for output parameters
    for (outputProperties in processEngineWorkerDocumentationInfo.outputProperties) {
      val property = createOutputParameterProp(outputProperties)
      elementTemplate.properties.add(property)
    }

    val jsonSchema = "https://unpkg.com/@camunda/element-templates-json-schema@0.1.0/resources/schema.json"
    val json = SchemaJsonConverter.toJsonString(jsonSchema, elementTemplate, CamundaC7ElementTemplate::class.java)

    return GenerationResult(
      name = processEngineWorkerDocumentationInfo.name,
      version = processEngineWorkerDocumentationInfo.version,
      content = json,
      fileName = processEngineWorkerDocumentationInfo.name.replace(" ", "-") + ".json",
      engine = TargetPlattform.C7
    )
  }

  private fun createInputParameterProp(
    info: ProcessEngineWorkerPropertyInfo,
    inputValueNamingPolicy: InputValueNamingPolicy
  ): Property {
    val property = Property()
      .withLabel("Input: ${info.label}")
      .withValue(
        when (inputValueNamingPolicy) {
          InputValueNamingPolicy.EMPTY -> "\${}"
          InputValueNamingPolicy.ATTRIBUTE_NAME -> "\${${info.name}}"
        }
      )
      .withType(info.type.type)
      .withChoices(null)
      .withBinding(
        Binding()
          .withType(Binding.Type.CAMUNDA_INPUT_PARAMETER)
          .withName(info.name)
      )
    property.constraints = Constraints()
      .withNotEmpty(info.notEmpty)
    property.editable = info.editable

    return property
  }

  private fun createOutputParameterProp(info: ProcessEngineWorkerPropertyInfo): Property {
    val property = Property()
      .withLabel("Output: ${info.label}")
      .withValue(info.name)
      .withType(info.type.type)
      .withChoices(null)
      .withBinding(
        Binding()
          .withType(Binding.Type.CAMUNDA_OUTPUT_PARAMETER)
          .withSource("\${${info.name}}")
      )
    property.constraints = Constraints()
      .withNotEmpty(info.notEmpty)
    property.editable = info.editable

    return property
  }

  private fun createExternalTaskProperty(): Property {
    return Property()
      .withLabel("Implementation Type")
      .withType(PropertyType.STRING.type)
      .withValue("external")
      .withEditable(false)
      // See Java comment: avoid empty list in JSON by setting choices to null
      .withChoices(null)
      .withBinding(
        Binding()
          .withType(Binding.Type.PROPERTY)
          .withName("camunda:type")
      )
  }

  private fun createExternalTaskTopicProperty(type: String): Property {
    return Property()
      .withLabel("Topic")
      .withType(PropertyType.STRING.type)
      .withValue(type)
      .withEditable(false)
      .withChoices(null)
      .withBinding(
        Binding()
          .withType(Binding.Type.PROPERTY)
          .withName("camunda:topic")
      )
  }

  private fun createAsyncBeforeProperty(asyncBefore: Boolean): Property {
    return Property()
      .withLabel("Async Before")
      .withType("Boolean")
      .withValue(asyncBefore)
      .withEditable(true)
      .withChoices(null)
      .withBinding(
        Binding()
          .withType(Binding.Type.PROPERTY)
          .withName("camunda:asyncBefore")
      )
  }

  private fun createAsyncAfterProperty(asyncAfter: Boolean): Property {
    return Property()
      .withLabel("Async After")
      .withType("Boolean")
      .withValue(asyncAfter)
      .withEditable(true)
      .withChoices(null)
      .withBinding(
        Binding()
          .withType(Binding.Type.PROPERTY)
          .withName("camunda:asyncAfter")
      )
  }

}
