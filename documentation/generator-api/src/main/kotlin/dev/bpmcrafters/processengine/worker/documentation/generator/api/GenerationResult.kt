package dev.bpmcrafters.processengine.worker.documentation.generator.api

data class GenerationResult(
  val name: String,
  val version : Int,
  val content: String,
  val fileName: String,
  val engine: TargetPlattform
)
