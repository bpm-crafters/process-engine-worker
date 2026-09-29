package dev.bpmcrafters.processengine.worker.registrar

import java.lang.reflect.UndeclaredThrowableException
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ExecutionException

/**
 * Utility to help unwrapping exceptions.
 * @since 0.0.3
 */
class ExceptionResolver {

  /**
   * Unpack the exception.
   * @param e thrown exception.
   * @return unwrapped cause.
   */
  fun getCause(e: Throwable): Throwable {
    return when (e) {
      is UndeclaredThrowableException -> if (e.undeclaredThrowable != null) {
        getCause(e.undeclaredThrowable!!)
      } else if (e.cause != null) {
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

      is ExecutionException -> if (e.cause != null) {
        getCause(e.cause!!)
      } else {
        e
      }

      else -> e
    }
  }
}
