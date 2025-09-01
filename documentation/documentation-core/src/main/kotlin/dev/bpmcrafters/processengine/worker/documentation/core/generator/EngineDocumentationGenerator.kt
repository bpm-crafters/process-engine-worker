package dev.bpmcrafters.processengine.worker.documentation.core.generator

import dev.bpmcrafters.processengine.worker.documentation.core.EngineSpecificConfig
import dev.bpmcrafters.processengine.worker.documentation.core.InputValueNamingPolicy
import dev.bpmcrafters.processengine.worker.documentation.core.TargetPlattform

interface EngineDocumentationGenerator {

  fun generate(processEngineWorkerDocumentationInfo: ProcessEngineWorkerDocumentationInfo, namingPolicy: InputValueNamingPolicy, engineSpecificConfig: EngineSpecificConfig): GenerationResult

  fun isSupported(targetPlattform: TargetPlattform): Boolean

}
