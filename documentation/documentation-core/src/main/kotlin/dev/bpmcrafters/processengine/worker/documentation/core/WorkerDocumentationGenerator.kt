package dev.bpmcrafters.processengine.worker.documentation.core

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.documentation.api.ProcessEngineWorkerDocumentation
import dev.bpmcrafters.processengine.worker.documentation.api.ProcessEngineWorkerPropertyDocumentation
import dev.bpmcrafters.processengine.worker.documentation.core.generator.EngineDocumentationGenerator
import dev.bpmcrafters.processengine.worker.documentation.core.generator.ProcessEngineWorkerDocumentationInfo
import dev.bpmcrafters.processengine.worker.documentation.core.generator.ProcessEngineWorkerPropertyInfo
import dev.bpmcrafters.processengine.worker.documentation.core.generator.engines.c7.Camunda7ElementTemplateGenerator
import org.apache.commons.io.FileUtils
import org.apache.maven.project.MavenProject
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder
import java.io.File
import java.io.IOException
import java.net.URL
import java.net.URLClassLoader

class WorkerDocumentationGenerator(
  val project: MavenProject,
  val engine: TargetPlattform,
  val outputDirectory: File,
  val engineSpecificConfig: EngineSpecificConfig,
  val inputValueNamingPolicy: InputValueNamingPolicy = InputValueNamingPolicy.EMPTY,
) {

  val engineDocumentationGenerators = listOf<EngineDocumentationGenerator>(
    Camunda7ElementTemplateGenerator()
  )

  /**
   * Clean the output directory
   */
  fun clean() {
    try {
      FileUtils.deleteDirectory(outputDirectory)
    } catch (e: IOException) {
      throw DocumentationFailedException("Failed to clean output directory", e)
    }
  }

  /**
   * Generates engine-specific documentation to the outputDirectory like e.g. c7 element templates
   */
  fun generate() {
    // Find annotated workers
    val classpathURLs = project.compileClasspathElements
      .map { File(it).toURI().toURL() }
    val urlClassLoader = URLClassLoader(classpathURLs.toTypedArray<URL?>(), javaClass.getClassLoader())
    val reflections = Reflections(
      ConfigurationBuilder()
        .setUrls(ClasspathHelper.forClassLoader(urlClassLoader))
        .addClassLoaders(urlClassLoader)
        .addScanners(Scanners.MethodsAnnotated)
    )
    val workers = reflections.getMethodsAnnotatedWith(ProcessEngineWorker::class.java)

    val engine = engineDocumentationGenerators
      .filter { it.isSupported(engine) }
      .let { if (it.size != 1) throw DocumentationFailedException("Expected exactly one engine documentation generator for $engine, but got ${it.size}", null) else it.first() }

    // generate documentation for each worker
    workers.forEach {
      if (it.parameterTypes.size > 1) {
        throw DocumentationFailedException("Worker method ${it.name} has more than one parameter.", null)
      }

      val inputParam = it.parameterTypes.first()
      val returnType = it.returnType

      try {
        val workerAnnotation = it.getAnnotation(ProcessEngineWorker::class.java)
        val documentationAnnotation = it.getAnnotation(ProcessEngineWorkerDocumentation::class.java)

        val processEngineWorkerDocumentation = ProcessEngineWorkerDocumentationInfo(
          documentationAnnotation.name,
          documentationAnnotation.description,
          documentationAnnotation.version,
          workerAnnotation.topic,
          generateProperties(inputParam),
          generateProperties(returnType)
        )

        val result = engine.generate(processEngineWorkerDocumentation, inputValueNamingPolicy, engineSpecificConfig)

        FileUtils.createParentDirectories(outputDirectory)
        val workerDocumentation = File(outputDirectory, result.fileName)
        FileUtils.write(workerDocumentation, result.content, "UTF-8")
      } catch (e: NullPointerException) {
        throw DocumentationFailedException("Worker method ${it.name} is missing @ProcessEngineWorkerDocumentation annotation.", e)
      }
    }
  }

  private fun generateProperties(type: Class<*>): List<ProcessEngineWorkerPropertyInfo> {
    return type.declaredFields.map { field ->
      val annotation = field.getAnnotation(ProcessEngineWorkerPropertyDocumentation::class.java)
      ProcessEngineWorkerPropertyInfo(
        name = field.name,
        label = annotation.label,
        type = annotation.type,
        editable = annotation.editable,
        notEmpty = annotation.notEmpty,
      )
    }
  }


}
