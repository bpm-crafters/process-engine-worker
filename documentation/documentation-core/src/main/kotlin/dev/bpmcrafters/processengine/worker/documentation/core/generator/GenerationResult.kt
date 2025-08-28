package dev.bpmcrafters.processengine.worker.documentation.core.generator

import dev.bpmcrafters.processengine.worker.documentation.core.TargetPlattform

data class GenerationResult(
  val name: String,
  val version : Int,
  val content: String,
  val fileName: String,
  val engine: TargetPlattform
)
