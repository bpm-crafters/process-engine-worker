package dev.bpmcrafters.processengine.worker

/**
 * Special exception indicating the task rejection.
 */
class RejectJobException(
  /**
   * Reason for rejection.
   */
  override val message: String
) : Exception()
