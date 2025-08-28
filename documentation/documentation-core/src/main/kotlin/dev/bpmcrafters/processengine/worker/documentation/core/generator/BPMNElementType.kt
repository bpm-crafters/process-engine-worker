package dev.bpmcrafters.processengine.worker.documentation.core.generator

enum class BPMNElementType(val value: String) {
  BPMN_SERVICE_TASK("bpmn:ServiceTask"),
  BPMN_SEND_TASK("bpmn:SendTask"),
  BPMN_INTERMEDIATE_THROW_EVENT("bpmn:IntermediateThrowEvent")
}
