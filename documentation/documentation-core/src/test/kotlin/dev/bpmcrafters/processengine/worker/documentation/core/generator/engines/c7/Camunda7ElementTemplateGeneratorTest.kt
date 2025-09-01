package dev.bpmcrafters.processengine.worker.documentation.core.generator.engines.c7

import dev.bpmcrafters.processengine.worker.documentation.api.PropertyType
import dev.bpmcrafters.processengine.worker.documentation.core.C7Config
import dev.bpmcrafters.processengine.worker.documentation.core.EngineSpecificConfig
import dev.bpmcrafters.processengine.worker.documentation.core.InputValueNamingPolicy
import dev.bpmcrafters.processengine.worker.documentation.core.TargetPlattform
import dev.bpmcrafters.processengine.worker.documentation.core.generator.ProcessEngineWorkerDocumentationInfo
import dev.bpmcrafters.processengine.worker.documentation.core.generator.ProcessEngineWorkerPropertyInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Camunda7ElementTemplateGeneratorTest {

  private val generator = Camunda7ElementTemplateGenerator()

  @Test
  fun `isSupported returns true only for C7`() {
    assertThat(generator.isSupported(TargetPlattform.C7)).isTrue()
  }

  @Test
  fun `generate builds expected JSON and metadata`() {
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

    val config = EngineSpecificConfig(c7 = C7Config(asyncBeforeDefaultValue = true, asyncAfterDefaultValue = false))

    val result = generator.generate(info, InputValueNamingPolicy.ATTRIBUTE_NAME, config)

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
