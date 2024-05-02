package dev.bpmcrafters.processengine.worker

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Variable(
  val name: String,
)
