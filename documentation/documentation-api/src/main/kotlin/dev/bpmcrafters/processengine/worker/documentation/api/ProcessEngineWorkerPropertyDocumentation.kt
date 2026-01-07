package dev.bpmcrafters.processengine.worker.documentation.api

annotation class ProcessEngineWorkerPropertyDocumentation(
  val type: PropertyType = PropertyType.STRING,
  val label: String,
  val notEmpty: Boolean = false,
  val editable: Boolean = true,
)
