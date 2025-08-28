package dev.bpmcrafters.processengine.worker.documentation.api

annotation class ProcessEngineWorkerDocumentation(
  val name: String,
  val description: String = "",
  val version: Int = -1
)
