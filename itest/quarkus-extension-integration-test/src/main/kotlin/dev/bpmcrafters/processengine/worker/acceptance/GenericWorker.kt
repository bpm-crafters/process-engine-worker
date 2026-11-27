package dev.bpmcrafters.processengine.worker.acceptance

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable

interface GenericWorker<T> {
  @ProcessEngineWorker(topic = "generic")
  fun execute(@Variable("payload") payload: T): Map<String, Any?>
}
