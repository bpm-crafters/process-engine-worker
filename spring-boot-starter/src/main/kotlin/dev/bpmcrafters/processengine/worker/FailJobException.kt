package dev.bpmcrafters.processengine.worker

import java.time.Duration

/**
 * Exception for failing a job.
 * Allows to set the available retries and the backoff time for the retry.
 */
class FailJobException(
  message: String,
  cause: Exception? = null,
  /**
   * Number of retries left.
   */
  val retryCount: Int,
  /**
   * Backoff time until retry possible.
   */
  val retryBackoff: Duration,
) : Exception(message, cause)
