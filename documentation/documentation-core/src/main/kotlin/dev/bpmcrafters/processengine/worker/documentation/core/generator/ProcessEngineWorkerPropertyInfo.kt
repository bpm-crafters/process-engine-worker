package dev.bpmcrafters.processengine.worker.documentation.core.generator

import dev.bpmcrafters.processengine.worker.documentation.api.PropertyType

data class ProcessEngineWorkerPropertyInfo(
  val name: String,
  val label: String,
  val type: PropertyType,
  val editable: Boolean,
  val notEmpty: Boolean,
)
