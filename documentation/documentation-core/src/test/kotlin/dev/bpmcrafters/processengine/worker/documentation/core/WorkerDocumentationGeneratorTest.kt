package dev.bpmcrafters.processengine.worker.documentation.core

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.documentation.api.ProcessEngineWorkerDocumentation
import dev.bpmcrafters.processengine.worker.documentation.api.ProcessEngineWorkerPropertyDocumentation
import dev.bpmcrafters.processengine.worker.documentation.generator.api.EngineDocumentationGeneratorApi
import dev.bpmcrafters.processengine.worker.documentation.generator.api.GenerationResult
import dev.bpmcrafters.processengine.worker.documentation.generator.api.TargetPlattform
import org.apache.maven.project.MavenProject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.`when`
import org.mockito.kotlin.*
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

  private val generationResult = GenerationResult(
    name = "Test Worker",
    fileName = "Test-Worker.json",
    content = "test",
    version = 0,
    engine = TargetPlattform.C7
  )

  @Test
  fun `generate creates element template file for annotated worker`() {
    val testClassesPath = File(this::class.java.protectionDomain.codeSource.location.toURI()).path

    val project = mock<MavenProject> {
      on { compileClasspathElements } doReturn(listOf(testClassesPath))
    }

    val engineGenerator = mock<EngineDocumentationGeneratorApi>()
    `when`(engineGenerator.generate(any())).thenReturn(generationResult)

    val generator = WorkerDocumentationGenerator(
      project = project,
      outputDirectory = tempDir,
      engineGenerator
    )

    generator.generate()

    val expectedFile = File(tempDir, "Test-Worker.json")
    assertThat(expectedFile).exists()
    val content = expectedFile.readText()
    assertThat(content)
      .isEqualTo("test")
    verify(engineGenerator).generate(any())
  }

  @Test
  fun `clean deletes output directory`() {
    val project = mock<MavenProject> {
      on { compileClasspathElements } doReturn(emptyList())
    }

    val engineGenerator = mock<EngineDocumentationGeneratorApi>()
    `when`(engineGenerator.generate(any())).thenReturn(generationResult)

    // create a file inside
    val testFile = File(tempDir, "some.txt").writeText("data")
    assertThat(tempDir)
      .exists()
    assertThat(testFile)

    val generator = WorkerDocumentationGenerator(
      project = project,
      outputDirectory = tempDir,
      engineGenerator
    )

    generator.clean()

    assertThat(tempDir.exists())
      .isFalse()
    verify(engineGenerator, never()).generate(any())
  }
}
