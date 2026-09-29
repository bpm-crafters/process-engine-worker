package dev.bpmcrafters.processengine.worker.registrar

interface ProcessEngineWorkerMetrics {

  /**
   * Task has been received because of the subscription of passed topic.
   * @param topic topic of the task.
   */
  fun taskReceived(topic: String)

  /**
   * Task has been completed with BPMN error for passed topic.
   * @param topic topic of the task.
   */
  fun taskCompletedByError(topic: String)

  /**
   * Task has been completed for passed topic.
   * @param topic topic of the task.
   */
  fun taskCompleted(topic: String)

  /**
   * Task processing failed for passed topic.
   * @param topic topic of the task.
   */
  fun taskFailed(topic: String)
}
