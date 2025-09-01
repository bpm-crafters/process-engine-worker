package dev.bpmcrafters.processengine.worker.documentation.core.generator.engines.c7

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import dev.bpmcrafters.processengine.worker.documentation.api.PropertyType
import dev.bpmcrafters.processengine.worker.documentation.c7.elementtemplates.gen.Binding
import dev.bpmcrafters.processengine.worker.documentation.c7.elementtemplates.gen.CamundaC7ElementTemplate
import dev.bpmcrafters.processengine.worker.documentation.c7.elementtemplates.gen.Constraints
import dev.bpmcrafters.processengine.worker.documentation.c7.elementtemplates.gen.Property
import dev.bpmcrafters.processengine.worker.documentation.core.EngineSpecificConfig
import dev.bpmcrafters.processengine.worker.documentation.core.InputValueNamingPolicy
import dev.bpmcrafters.processengine.worker.documentation.core.TargetPlattform
import dev.bpmcrafters.processengine.worker.documentation.core.generator.*

class Camunda7ElementTemplateGenerator: EngineDocumentationGenerator {
  override fun generate(processEngineWorkerDocumentationInfo: ProcessEngineWorkerDocumentationInfo, namingPolicy: InputValueNamingPolicy, engineSpecificConfig: EngineSpecificConfig): GenerationResult {
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
    val asyncBeforeProperty = createAsyncBeforeProperty(engineSpecificConfig.c7.asyncBeforeDefaultValue)
    elementTemplate.properties.add(asyncBeforeProperty)


    // Add asyncAfter property
    val asyncAfterProperty = createAsyncAfterProperty(engineSpecificConfig.c7.asyncAfterDefaultValue)
    elementTemplate.properties.add(asyncAfterProperty)


    // Add properties for input parameters
    for (inputProperty in processEngineWorkerDocumentationInfo.inputProperties) {
      val property = createInputParameterProp(inputProperty, namingPolicy)
      elementTemplate.properties.add(property)
    }


    // Add properties for output parameters
    for (outputProperties in processEngineWorkerDocumentationInfo.outputProperties) {
      val property = createOutputParameterProp(outputProperties)
      elementTemplate.properties.add(property)
    }

    val json = toJsonString(elementTemplate)
    return GenerationResult(
        name = processEngineWorkerDocumentationInfo.name,
        version = processEngineWorkerDocumentationInfo.version,
        content = json,
        fileName = processEngineWorkerDocumentationInfo.name.replace(" ", "-") + ".json",
        engine = TargetPlattform.C7
    )
  }

  override fun isSupported(targetPlattform: TargetPlattform): Boolean {
    return TargetPlattform.C7 == targetPlattform
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

  private fun toJsonString(elementTemplate: CamundaC7ElementTemplate): String {
    try {
      val jsonSchema = "https://unpkg.com/@camunda/element-templates-json-schema@0.1.0/resources/schema.json"
      val mapper = JsonMapper.builder()
        .configure(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST, true)
        .addMixIn(CamundaC7ElementTemplate::class.java, SchemaMixin::class.java)
        .build()
      val objectWriter = mapper.writerFor(CamundaC7ElementTemplate::class.java)
        .withAttribute("\$schema", jsonSchema)
      return objectWriter.withDefaultPrettyPrinter().writeValueAsString(elementTemplate)
    } catch (e: JsonProcessingException) {
      throw RuntimeException("Could not generate json string!", e)
    }
  }

}
