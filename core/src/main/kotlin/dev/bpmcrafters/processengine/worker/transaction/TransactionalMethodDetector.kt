package dev.bpmcrafters.processengine.worker.transaction

import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method

/**
 * Strategy detecting whether a worker method should be executed (together with the completion of the task)
 * inside a transaction.
 * @since 0.8.6
 */
fun interface TransactionalMethodDetector {

  /**
   * Checks if the method of the worker is transactional.
   * @param method worker method.
   * @return true, if the method should be executed transactional and be atomic with completion of the worker.
   */
  fun isTransactional(method: Method): Boolean

  companion object {

    /**
     * Detects `jakarta.transaction.Transactional` on the method or its declaring class
     * with one of the types `REQUIRED`, `REQUIRES_NEW`, `SUPPORTS` or `MANDATORY`.
     */
    @JvmField
    val JAKARTA: TransactionalMethodDetector = TransactionalMethodDetector { method ->
      method.isJakartaTransactionRequired() || method.declaringClass.isJakartaTransactionRequired()
    }

    /**
     * Detects `org.springframework.transaction.annotation.Transactional` on the method or its declaring class
     * with one of the propagations `REQUIRED`, `REQUIRES_NEW`, `SUPPORTS` or `MANDATORY`.
     * The detection is performed by annotation name, so no Spring dependency is required.
     */
    @JvmField
    val SPRING: TransactionalMethodDetector = TransactionalMethodDetector { method ->
      method.isSpringTransactionalRequired() || method.declaringClass.isSpringTransactionalRequired()
    }

    /**
     * Default detector, combining [JAKARTA] and [SPRING].
     */
    @JvmField
    val DEFAULT: TransactionalMethodDetector = anyOf(JAKARTA, SPRING)

    /**
     * Creates a detector which is positive, if any of the passed detectors is positive.
     * @param detectors detectors to combine.
     * @return combined detector.
     */
    @JvmStatic
    fun anyOf(vararg detectors: TransactionalMethodDetector): TransactionalMethodDetector =
      TransactionalMethodDetector { method -> detectors.any { it.isTransactional(method) } }

  }
}

private const val SPRING_TRANSACTIONAL = "org.springframework.transaction.annotation.Transactional"
private val SPRING_TRANSACTIONAL_PROPAGATIONS = setOf("REQUIRED", "REQUIRES_NEW", "SUPPORTS", "MANDATORY")
private val JAKARTA_TRANSACTIONAL_TYPES = setOf(
  jakarta.transaction.Transactional.TxType.REQUIRED,
  jakarta.transaction.Transactional.TxType.REQUIRES_NEW,
  jakarta.transaction.Transactional.TxType.SUPPORTS,
  jakarta.transaction.Transactional.TxType.MANDATORY
)

private fun AnnotatedElement.isJakartaTransactionRequired(): Boolean =
  this.getAnnotation(jakarta.transaction.Transactional::class.java)?.let { it.value in JAKARTA_TRANSACTIONAL_TYPES } ?: false

private fun AnnotatedElement.isSpringTransactionalRequired(): Boolean =
  this.annotations
    .firstOrNull { it.annotationClass.java.name == SPRING_TRANSACTIONAL }
    ?.let { annotation ->
      val propagation = annotation.annotationClass.java.getMethod("propagation").invoke(annotation)
      propagation?.toString() in SPRING_TRANSACTIONAL_PROPAGATIONS
    } ?: false
