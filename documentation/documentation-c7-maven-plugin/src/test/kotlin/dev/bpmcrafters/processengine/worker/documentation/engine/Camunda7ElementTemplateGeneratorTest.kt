package dev.bpmcrafters.processengine.worker.documentation.engine

import dev.bpmcrafters.processengine.worker.documentation.C7Config
import dev.bpmcrafters.processengine.worker.documentation.api.PropertyType
import dev.bpmcrafters.processengine.worker.documentation.generator.api.InputValueNamingPolicy
import dev.bpmcrafters.processengine.worker.documentation.generator.api.ProcessEngineWorkerDocumentationInfo
import dev.bpmcrafters.processengine.worker.documentation.generator.api.ProcessEngineWorkerPropertyInfo
import dev.bpmcrafters.processengine.worker.documentation.generator.api.TargetPlattform
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Camunda7ElementTemplateGeneratorTest {

  @Test
  fun `generate builds expected JSON and metadata`() {
    val config = C7Config(asyncBeforeDefaultValue = true, asyncAfterDefaultValue = false)

    val generator = Camunda7ElementTemplateGenerator(c7Config = config, inputValueNamingPolicy = InputValueNamingPolicy.ATTRIBUTE_NAME)

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
      .hasFieldOrPropertyWithValue("engine", TargetPlattform.C7)
    assertThat(result.content)
      .contains("\"\$schema\"")
      .contains("\"name\" : \"My Worker\"")
      .contains("\"id\" : \"myTopic\"")
      .contains("\"camunda:type\"")
      .contains("\"external\"")
      .contains("\"camunda:topic\"")
      .contains("\"myTopic\"")
      .contains("Input: Foo")
      .contains("Output: Bar")
      .contains("Async Before")
      .contains("Async After")
  }

}
