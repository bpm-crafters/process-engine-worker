package dev.bpmcrafters.processengine.worker.registrar

import org.springframework.transaction.TransactionException
import org.springframework.transaction.TransactionSystemException
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
      is InvocationTargetException -> if (e.targetException != null) {
        getCause(e.targetException!!)
      } else if (e.cause != null) {
        getCause(e.cause!!)
      } else {
        e
      }
      is TransactionSystemException -> if (e.applicationException != null) {
        getCause(e.applicationException!!)
      } else if (e.cause != null) {
        getCause(e.cause!!)
      } else {
        e
      }
      is ExecutionException, is TransactionException -> if (e.cause != null) {
        getCause(e.cause!!)
      } else {
        e
      }
      else -> e
    }
  }
}
