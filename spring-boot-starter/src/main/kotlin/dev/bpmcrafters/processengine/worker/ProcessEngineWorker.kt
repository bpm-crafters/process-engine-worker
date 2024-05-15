package dev.bpmcrafters.processengine.worker

/**
 * Indicates a method to be a worker.
 */
@MustBeDocumented
@Target(AnnotationTarget.FUNCTION)
annotation class ProcessEngineWorker(
  /**
   * Topic name to subscribe this worker for.
   */
  val topic: String = DEFAULT_UNSET_TOPIC
) {
  companion object {
    /**
     * Null value for the topic.
     */
    const val DEFAULT_UNSET_TOPIC = "__unset"
  }
}
