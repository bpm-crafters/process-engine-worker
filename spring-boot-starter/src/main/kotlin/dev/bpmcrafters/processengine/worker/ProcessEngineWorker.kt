package dev.bpmcrafters.processengine.worker

/**
 * Indicates a method to be a worker.
 * @since 0.0.1
 */
@MustBeDocumented
@Target(AnnotationTarget.FUNCTION)
annotation class ProcessEngineWorker(
  /**
   * Topic name to subscribe this worker for.
   */
  val topic: String = DEFAULT_UNSET_TOPIC,
  /**
   * Flag, indicating if the task should be automatically completed after the execution of the worker.
   * Defaults to true. If the return type of the annotated method is Map<String, Any> it will overrule
   * this setting and auto-complete the task with payload taken from the return value of the method.
   */
  val autoComplete: Boolean = true,
  /**
   * Configures when this worker completes a task if auto complete is active.
   *
   * This value has no effect if the worker is not transactional.
   *
   * @see autoComplete
   */
  val completion: Completion = Completion.DEFAULT
) {
  companion object {
    /**
     * Null value for the topic.
     */
    const val DEFAULT_UNSET_TOPIC = "__unset"
  }

  enum class Completion {
    DEFAULT,
    IN_TRANSACTION,
    AFTER_TRANSACTION,
  }
}
