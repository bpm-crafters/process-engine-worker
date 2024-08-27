package dev.bpmcrafters.processengine.worker.registrar

import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ExecutionException

/**
 * Utility to help unwrapping exceptions.
 */
class ExceptionResolver {

  /**
   * Unpack the exception.
   * @param e thrown exception.
   * @return unwrapped cause.
   */
  fun getCause(e: Throwable): Throwable {
    return when (e) {
      is ExecutionException -> if (e.cause != null) {
        getCause(e.cause!!)
      } else {
        e
      }
      is InvocationTargetException -> if (e.targetException != null) {
        getCause(e.targetException!!)
      } else if (e.cause != null) {
        getCause(e.cause!!)
      } else {
        e
      }
      else -> e
    }
  }
}
