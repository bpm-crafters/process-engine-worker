package dev.bpmcrafters.processengine.worker.documentation.engine

import dev.bpmcrafters.processengine.worker.documentation.api.PropertyType
import dev.bpmcrafters.processengine.worker.documentation.generator.api.InputValueNamingPolicy
import dev.bpmcrafters.processengine.worker.documentation.generator.api.ProcessEngineWorkerDocumentationInfo
import dev.bpmcrafters.processengine.worker.documentation.generator.api.ProcessEngineWorkerPropertyInfo
import dev.bpmcrafters.processengine.worker.documentation.generator.api.TargetPlattform
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Camunda8ElementTemplateGeneratorTest {

  @Test
  fun `generate builds expected JSON and metadata`() {
    val generator = Camunda8ElementTemplateGenerator(inputValueNamingPolicy = InputValueNamingPolicy.ATTRIBUTE_NAME)

    val info = ProcessEngineWorkerDocumentationInfo(
      name = "My Worker",
      description = "desc",
      version = -1,
      type = "myTopic",
      inputProperties = listOf(
        ProcessEngineWorkerPropertyInfo(
          name = "foo",
          label = "Foo",
          type = PropertyType.STRING,
          editable = true,
          notEmpty = false
        )
      ),
      outputProperties = listOf(
        ProcessEngineWorkerPropertyInfo(
          name = "bar",
          label = "Bar",
          type = PropertyType.STRING,
          editable = true,
          notEmpty = false
        )
      )
    )

    val result = generator.generate(info)

    assertThat(result)
      .hasFieldOrPropertyWithValue("name", info.name)
      .hasFieldOrPropertyWithValue("fileName", "My-Worker.json")
      .hasFieldOrPropertyWithValue("engine", TargetPlattform.C8)
    assertThat(result.content)
      .contains("\"\$schema\"")
      .contains("\"name\" : \"My Worker\"")
      .contains("\"id\" : \"myTopic\"")
      .contains("\"zeebe:taskDefinition:type\"")
      .contains("\"Topic\"")
      .contains("\"myTopic\"")
      .contains("Input: Foo")
      .contains("Output: Bar")
  }

}
