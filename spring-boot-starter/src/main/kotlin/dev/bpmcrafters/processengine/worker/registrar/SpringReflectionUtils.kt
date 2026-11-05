package dev.bpmcrafters.processengine.worker.registrar

import java.lang.reflect.Method

/** Resolves transaction metadata owned by the Spring adapter. */
fun Method.isTransactional() =
  this.getAnnotation(org.springframework.transaction.annotation.Transactional::class.java)
    ?.isTransactionalRequired() ?: false
    || this.declaringClass.getAnnotation(org.springframework.transaction.annotation.Transactional::class.java)
    ?.isTransactionalRequired() ?: false
    || this.getAnnotation(jakarta.transaction.Transactional::class.java)?.isTransactionRequired() ?: false
    || this.declaringClass.getAnnotation(jakarta.transaction.Transactional::class.java)
    ?.isTransactionRequired() ?: false

private fun org.springframework.transaction.annotation.Transactional.isTransactionalRequired() =
  this.propagation == org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
    || this.propagation == org.springframework.transaction.annotation.Propagation.REQUIRED
    || this.propagation == org.springframework.transaction.annotation.Propagation.SUPPORTS
    || this.propagation == org.springframework.transaction.annotation.Propagation.MANDATORY

private fun jakarta.transaction.Transactional.isTransactionRequired() =
  this.value == jakarta.transaction.Transactional.TxType.REQUIRED
    || this.value == jakarta.transaction.Transactional.TxType.REQUIRES_NEW
    || this.value == jakarta.transaction.Transactional.TxType.SUPPORTS
    || this.value == jakarta.transaction.Transactional.TxType.MANDATORY
