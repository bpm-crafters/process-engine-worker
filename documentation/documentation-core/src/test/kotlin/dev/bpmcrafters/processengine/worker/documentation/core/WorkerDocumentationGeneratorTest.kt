package dev.bpmcrafters.processengine.worker.documentation.core

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.documentation.api.ProcessEngineWorkerDocumentation
import dev.bpmcrafters.processengine.worker.documentation.api.ProcessEngineWorkerPropertyDocumentation
import org.apache.maven.project.MavenProject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File

class WorkerDocumentationGeneratorTest {

  // Test helper types that will be discovered by Reflections
  data class InDto(
    @field:ProcessEngineWorkerPropertyDocumentation(label = "In Label")
    val inField: String
  )
  data class OutDto(
    @field:ProcessEngineWorkerPropertyDocumentation(label = "Out Label")
    val outField: String
  )
  class TestWorkerClass {
    @ProcessEngineWorker(topic = "testTopic")
    @ProcessEngineWorkerDocumentation(name = "Test Worker", description = "desc", version = 1)
    fun work(input: InDto): OutDto = OutDto("x")
  }

  @TempDir
  lateinit var tempDir: File

  @Test
  fun `generate creates element template file for annotated worker`() {
    val testClassesPath = File(this::class.java.protectionDomain.codeSource.location.toURI()).path

    val project = mock<MavenProject> {
      on { compileClasspathElements } doReturn(listOf(testClassesPath))
    }

    val generator = WorkerDocumentationGenerator(
      project = project,
      engine = TargetPlattform.C7,
      outputDirectory = tempDir,
      engineSpecificConfig = EngineSpecificConfig(),
      inputValueNamingPolicy = InputValueNamingPolicy.EMPTY
    )

    // when
    generator.generate()

    // then
    val expectedFile = File(tempDir, "Test-Worker.json")
    assertThat(expectedFile).exists()
    val content = expectedFile.readText()
    assertThat(content)
      .contains("\"name\" : \"Test Worker\"")
      .contains("\"id\" : \"testTopic\"")
      .contains("Input: In Label")
      .contains("Output: Out Label")
  }

  @Test
  fun `clean deletes output directory`() {
    val project = mock<MavenProject> {
      on { compileClasspathElements } doReturn(emptyList())
    }

    // create a file inside
    val testFile = File(tempDir, "some.txt").writeText("data")
    assertThat(tempDir)
      .exists()
    assertThat(testFile)

    val generator = WorkerDocumentationGenerator(
      project = project,
      engine = TargetPlattform.C7,
      outputDirectory = tempDir,
      engineSpecificConfig = EngineSpecificConfig(),
      inputValueNamingPolicy = InputValueNamingPolicy.EMPTY
    )

    generator.clean()

    assertThat(tempDir.exists())
      .isFalse()
  }
}
