package dev.bpmcrafters.processengine.worker.documentation.core.generator

data class ProcessEngineWorkerDocumentationInfo(
  val name: String,
  val description: String,
  val version: Int,
  val type: String,
  val inputProperties: List<ProcessEngineWorkerPropertyInfo>,
  val outputProperties: List<ProcessEngineWorkerPropertyInfo>
)
