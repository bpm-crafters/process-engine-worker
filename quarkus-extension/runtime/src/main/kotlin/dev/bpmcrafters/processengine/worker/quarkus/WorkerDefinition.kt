package dev.bpmcrafters.processengine.worker.quarkus

import io.quarkus.runtime.annotations.RecordableConstructor

/** Build-time Arc identity and effective transaction metadata, not a class-based CDI lookup. */
class WorkerDefinition @RecordableConstructor constructor(
  val beanIdentifier: String,
  val beanClass: String,
  val methodDescriptor: String,
  val transactionType: String?,
  rollbackOn: List<String>,
  dontRollbackOn: List<String>
) {
  val rollbackOn: List<String> = java.util.List.copyOf(rollbackOn)
  val dontRollbackOn: List<String> = java.util.List.copyOf(dontRollbackOn)

  fun beanIdentifier() = beanIdentifier
  fun beanClass() = beanClass
  fun methodDescriptor() = methodDescriptor
  fun transactionType() = transactionType
  fun rollbackOn(): List<String> = rollbackOn
  fun dontRollbackOn(): List<String> = dontRollbackOn

  override fun equals(other: Any?): Boolean = other is WorkerDefinition &&
    beanIdentifier == other.beanIdentifier && beanClass == other.beanClass &&
    methodDescriptor == other.methodDescriptor && transactionType == other.transactionType &&
    rollbackOn == other.rollbackOn && dontRollbackOn == other.dontRollbackOn

  override fun hashCode(): Int = listOf(beanIdentifier, beanClass, methodDescriptor, transactionType, rollbackOn, dontRollbackOn)
    .fold(0) { hash, value -> 31 * hash + (value?.hashCode() ?: 0) }

  override fun toString(): String = "WorkerDefinition[beanIdentifier=$beanIdentifier, beanClass=$beanClass, " +
    "methodDescriptor=$methodDescriptor, transactionType=$transactionType, rollbackOn=$rollbackOn, dontRollbackOn=$dontRollbackOn]"
}
