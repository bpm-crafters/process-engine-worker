package dev.bpmcrafters.processengine.worker

import org.springframework.core.annotation.AliasFor

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
  @get: AliasFor(attribute = "value")
  val topic: String = DEFAULT_UNSET_TOPIC,
  /**
   * Topic name to subscribe this worker for.
   */
  @get: AliasFor(attribute = "topic")
  val value: String = DEFAULT_UNSET_TOPIC,
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
  val completion: Completion = Completion.DEFAULT,
  /**
   * Optional lock duration in seconds for this worker.
   * If not specified (default: -1), the adapter's global configuration will be used.
   * @since 0.8.0
   */
  val lockDuration: Long = DEFAULT_UNSET_LOCK_DURATION
) {
  companion object {
    /**
     * Null value for the topic.
     */
    const val DEFAULT_UNSET_TOPIC = "__unset"
    /**
     * Sentinel value indicating lock duration is not specified.
     */
    const val DEFAULT_UNSET_LOCK_DURATION = -1L
  }

  /**
   * Completion constants.
   */
  enum class Completion {
    /**
     * Use default configured via property.
     */
    DEFAULT,
    /**
     * Execute external task completion before the transaction is committed.
     */
    BEFORE_COMMIT,
    /**
     * Execute external task completion after transaction is committed.
     */
    AFTER_COMMIT,
  }
}
