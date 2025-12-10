package dev.bpmcrafters.processengine.worker.documentation

import dev.bpmcrafters.processengine.worker.documentation.core.WorkerDocumentationGenerator
import dev.bpmcrafters.processengine.worker.documentation.generator.api.DocumentationFailedException
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.project.MavenProject
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.io.File

class WorkerDocumentationGeneratorMojoTest {

  @Test
  fun `execute with clean true calls clean and generate`() {
    val mojo = WorkerDocumentationGeneratorMojo().apply {
      project = mock<MavenProject>()
      outputDirectory = mock<File>()
      clean = true
    }

    val mockGenerator = mock<WorkerDocumentationGenerator>()
    mojo.generatorFactory = { _, _, _ -> mockGenerator }

    mojo.execute()

    verify(mockGenerator).clean()
    verify(mockGenerator).generate()
  }

  @Test
  fun `execute with clean false calls only generate`() {
    val mojo = WorkerDocumentationGeneratorMojo().apply {
      project = mock<MavenProject>()
      outputDirectory = mock<File>()
      clean = false
    }

    val mockGenerator = mock<WorkerDocumentationGenerator>()
    mojo.generatorFactory = { _, _, _ -> mockGenerator }

    mojo.execute()

    verify(mockGenerator, never()).clean()
    verify(mockGenerator).generate()
  }

  @Test
  fun `execute wraps DocumentationFailedException into MojoExecutionException`() {
    val mojo = WorkerDocumentationGeneratorMojo().apply {
      project = mock<MavenProject>()
      outputDirectory = mock<File>()
      clean = false
    }

    val failingGenerator = mock<WorkerDocumentationGenerator> {
      on { generate() } doThrow DocumentationFailedException("boom", null)
    }
    mojo.generatorFactory = { _, _, _ -> failingGenerator }

    assertThatThrownBy { mojo.execute() }
      .isInstanceOf(MojoExecutionException::class.java)
  }
}
