package dev.bpmcrafters.processengine.worker.documentation.generator.api

enum class BPMNElementType(val value: String) {
  BPMN_SERVICE_TASK("bpmn:ServiceTask"),
  BPMN_SEND_TASK("bpmn:SendTask"),
  BPMN_INTERMEDIATE_THROW_EVENT("bpmn:IntermediateThrowEvent")
}
