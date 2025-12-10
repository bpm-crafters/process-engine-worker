package dev.bpmcrafters.processengine.worker.documentation.generator.api

interface EngineDocumentationGeneratorApi {

  fun generate(processEngineWorkerDocumentationInfo: ProcessEngineWorkerDocumentationInfo): GenerationResult

}
