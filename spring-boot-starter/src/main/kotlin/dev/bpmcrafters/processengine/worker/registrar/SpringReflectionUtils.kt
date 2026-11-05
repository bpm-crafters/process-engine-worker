package dev.bpmcrafters.processengine.worker.registrar

import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import java.lang.reflect.Method

private val transactionAttributes = AnnotationTransactionAttributeSource()

/** Resolves effective Spring/Jakarta transaction metadata, including composed annotations and overrides. */
fun Method.isTransactional(): Boolean = isTransactional(declaringClass)

/** Uses the same target-aware attribute precedence as Spring transaction interception. */
fun Method.isTransactional(targetClass: Class<*>): Boolean =
  when (transactionAttributes.getTransactionAttribute(this, targetClass)?.propagationBehavior) {
    TransactionDefinition.PROPAGATION_REQUIRED,
    TransactionDefinition.PROPAGATION_REQUIRES_NEW,
    TransactionDefinition.PROPAGATION_SUPPORTS,
    TransactionDefinition.PROPAGATION_MANDATORY -> true
    else -> false
  }
