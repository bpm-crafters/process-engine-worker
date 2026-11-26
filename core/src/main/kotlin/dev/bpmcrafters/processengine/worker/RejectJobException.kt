package dev.bpmcrafters.processengine.worker

/**
 * Special exception indicating the task rejection.
 * @since 0.4.3
 **/
class RejectJobException(
  /**
   * Reason for rejection.
   **/
  override val message: String
) : Exception()
