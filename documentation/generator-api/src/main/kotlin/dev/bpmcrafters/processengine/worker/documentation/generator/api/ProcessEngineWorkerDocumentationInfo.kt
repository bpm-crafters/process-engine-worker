package dev.bpmcrafters.processengine.worker.documentation.generator.api

data class ProcessEngineWorkerDocumentationInfo(
  val name: String,
  val description: String,
  val version: Int,
  val type: String,
  val inputProperties: List<ProcessEngineWorkerPropertyInfo>,
  val outputProperties: List<ProcessEngineWorkerPropertyInfo>
)
