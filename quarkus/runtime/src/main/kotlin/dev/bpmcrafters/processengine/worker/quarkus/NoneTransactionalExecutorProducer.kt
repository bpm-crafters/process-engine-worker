package dev.bpmcrafters.processengine.worker.quarkus

import dev.bpmcrafters.processengine.worker.transaction.TransactionalExecutor
import io.quarkus.arc.DefaultBean
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton

/**
 * Produces the default transactional executor without transaction management ([TransactionalExecutor.NONE]).
 * The bean is a `@Singleton` (no client proxy), so the registration can detect the missing transaction management by identity.
 * Registered by the deployment module only if the transactions capability (`quarkus-narayana-jta`) is missing,
 * otherwise [JtaTransactionalExecutor] is registered; excluded from bean discovery if not registered.
 * @since 0.8.6
 */
@Singleton
open class NoneTransactionalExecutorProducer {

  /**
   * Produces the executor running blocks without transactions.
   * @return transactional executor.
   */
  @Produces
  @Singleton
  @DefaultBean
  open fun noneTransactionalExecutor(): TransactionalExecutor = TransactionalExecutor.NONE
}
