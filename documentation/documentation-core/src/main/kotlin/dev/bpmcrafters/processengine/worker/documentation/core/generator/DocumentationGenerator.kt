package dev.bpmcrafters.processengine.worker.documentation.core.generator

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.documentation.api.ProcessEngineWorkerDocumentation
import dev.bpmcrafters.processengine.worker.documentation.api.ProcessEngineWorkerPropertyDocumentation
import dev.bpmcrafters.processengine.worker.documentation.core.InputValueNamingPolicy
import dev.bpmcrafters.processengine.worker.documentation.core.TargetPlattform
import dev.bpmcrafters.processengine.worker.documentation.core.generator.engines.c7.Camunda7ElementTemplateGenerator
import org.apache.commons.io.FileUtils
import java.io.File

class DocumentationGenerator(
  val outputPath: File,
  val engine: TargetPlattform,
  val namingPolicy: InputValueNamingPolicy = InputValueNamingPolicy.EMPTY
) {

  val engineDocumentationGenerators = listOf<EngineDocumentationGenerator>(
    Camunda7ElementTemplateGenerator()
  )

  fun generate(
    workerAnnotation: ProcessEngineWorker,
    documentationAnnotation: ProcessEngineWorkerDocumentation,
    parameter: Class<*>,
    returnType: Class<*>
  ) {
      val processEngineWorkerDocumentation = ProcessEngineWorkerDocumentationInfo(
        documentationAnnotation.name,
        documentationAnnotation.description,
        documentationAnnotation.version,
        workerAnnotation.topic,
        generateProperties(parameter),
        generateProperties(returnType)
      )

    val engine = engineDocumentationGenerators
      .filter { it.isSupported(engine) }
      .let { if (it.size != 1) throw RuntimeException("Expected exactly one engine documentation generator for $engine, but got ${it.size}") else it.first() }

    val result = engine.generate(processEngineWorkerDocumentation, namingPolicy)

    FileUtils.createParentDirectories(outputPath)

    val workerDocumentation = File(outputPath, result.fileName)
    FileUtils.write(workerDocumentation, result.content, "UTF-8")
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
