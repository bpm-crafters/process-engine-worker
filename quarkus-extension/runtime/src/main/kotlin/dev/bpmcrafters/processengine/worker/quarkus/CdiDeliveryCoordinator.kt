package dev.bpmcrafters.processengine.worker.quarkus

import dev.bpmcrafters.processengine.worker.registrar.ExceptionResolver
import dev.bpmcrafters.processengine.worker.registrar.WorkerDelivery
import dev.bpmcrafters.processengine.worker.registrar.WorkerDeliveryCoordinator
import jakarta.interceptor.InvocationContext
import jakarta.transaction.*
import jakarta.transaction.Transactional.TxType
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.UndeclaredThrowableException
import java.util.ArrayDeque
import java.util.concurrent.Callable

/** Coordinates core phases at the actual CDI transaction boundary, never on an unwrapped target. */
class CdiDeliveryCoordinator(
  private val definition: WorkerDefinition,
  private val method: Method,
  private val transactions: TransactionManager,
  private val lifecycle: DeliveryLifecycle
) : WorkerDeliveryCoordinator {
  private val type = definition.transactionType()?.let(TxType::valueOf)

  override fun deliver(delivery: WorkerDelivery) {
    try {
      coordinate(delivery)
    } catch (failure: Throwable) {
      reportFailureOutsideTransaction(delivery, failure)
    }
  }

  private fun coordinate(delivery: WorkerDelivery) {
    if (type == TxType.NEVER && transactions.transaction != null) {
      throw InvalidTransactionException("NEVER worker cannot execute in an ambient transaction")
    }
    if (type == TxType.REQUIRES_NEW) {
      if (!lookupInNewTransaction(delivery)) invoke(Token(delivery, owned = false, cdiOwned = true))
      return
    }
    val suspended = if (type == TxType.NOT_SUPPORTED) transactions.suspend() else null
    try {
      val owned = needsEnvelope() && transactions.transaction == null
      if (owned) transactions.begin()
      execute(Token(delivery, owned, cdiOwned = false))
    } finally {
      if (suspended != null) transactions.resume(suspended)
    }
  }

  private fun needsEnvelope(): Boolean = type in setOf(TxType.REQUIRED, TxType.SUPPORTS, TxType.MANDATORY)

  private fun lookupInNewTransaction(delivery: WorkerDelivery): Boolean {
    val suspended = transactions.suspend()
    val token = Token(delivery, owned = true, cdiOwned = false)
    try {
      transactions.begin()
      var cached: Map<String, Any?>? = null
      var failure: Throwable? = null
      try {
        cached = infrastructure(delivery::cachedResult)
        if (cached != null) token.finish(cached)
      } catch (lookupFailure: Throwable) {
        failure = lookupFailure
      }
      failure = completeEnvelope(token, endEnvelope(token, failure))
      if (failure != null) throw reportable(failure)
      return cached != null
    } catch (failure: Throwable) {
      rollbackOwned(failure)
      throw reportable(failure)
    } finally {
      if (suspended != null) transactions.resume(suspended)
    }
  }

  private fun execute(token: Token) {
    var failure: Throwable? = null
    try {
      token.prepareBorrowedTransaction()
      val cached = infrastructure(token.delivery::cachedResult)
      if (cached != null) token.finish(cached) else invokeManaged(token)
    } catch (invocationFailure: Throwable) {
      failure = invocationFailure
      token.cancelDeferred()
    }
    failure = completeEnvelope(token, endEnvelope(token, failure))
    if (failure != null) throw reportable(failure)
  }

  private fun completeEnvelope(token: Token, failure: Throwable?): Throwable? {
    if (token.owned && token.outcome == Status.STATUS_COMMITTED) {
      try {
        token.completeOwned()
      } catch (completionFailure: Throwable) {
        return combine(failure, completionFailure)
      }
    }
    return failure
  }

  private fun endEnvelope(token: Token, failure: Throwable?): Throwable? {
    try {
      if (failure != null && (token.owned || needsEnvelope()) && rollbackOn(token, failure)) transactions.setRollbackOnly()
      if (token.owned) {
        if (failure != null && transactions.status == Status.STATUS_MARKED_ROLLBACK) transactions.rollback()
        else {
          transactions.commit()
          token.outcome = Status.STATUS_COMMITTED
        }
      }
    } catch (endFailure: Throwable) {
      if (token.owned) rollbackOwned(endFailure)
      return combine(failure, endFailure)
    }
    return failure
  }

  private fun invoke(token: Token) {
    var failure: Throwable? = null
    try {
      invokeManaged(token)
    } catch (invocationFailure: Throwable) {
      failure = invocationFailure
    }
    // CDI has committed/resumed its suspended caller, even if an outer interceptor then failed.
    try {
      token.completeCdiOwned()
    } catch (completionFailure: Throwable) {
      failure = combine(failure, completionFailure)
    }
    if (failure != null) throw reportable(failure)
  }

  private fun invokeManaged(token: Token) {
    val stack = TOKENS.get()
    stack.push(token)
    try {
      token.delivery.invoke()
      check(token.entered) { "Managed worker invocation missed its delivery interceptor: $method" }
      token.boundaryFailure?.let { throw reportable(it) }
    } finally {
      stack.pop()
      if (stack.isEmpty()) TOKENS.remove()
    }
  }

  /** Install the claimable token after argument resolution, immediately around the managed reference call. */
  fun invokeReference(invocation: Callable<Any?>): Any? {
    val pending = TOKENS.get().peek() ?: error("No delivery invocation is pending")
    val references = REFERENCES.get()
    references.push(pending)
    try {
      return invocation.call()
    } catch (failure: InvocationTargetException) {
      pending.referenceFailure = failure.targetException
      throw failure
    } finally {
      references.pop()
      if (references.isEmpty()) REFERENCES.remove()
    }
  }

  private fun rollbackOn(token: Token, failure: Throwable): Boolean {
    // Rollback sees the exception crossing CDI; reporting instead unwraps recursively.
    val outward = token.referenceFailure ?: token.boundaryFailure ?: failure
    val cause = if (outward is UndeclaredThrowableException && token.boundaryFailure != null &&
      outward.undeclaredThrowable === token.boundaryFailure) token.boundaryFailure else outward
    if (definition.dontRollbackOn().any { exceptionType(it).isInstance(cause) }) return false
    if (definition.rollbackOn().any { exceptionType(it).isInstance(cause) }) return true
    return cause is RuntimeException || cause is Error
  }

  private fun exceptionType(name: String): Class<*> = Class.forName(name, false, Thread.currentThread().contextClassLoader)

  private fun rollbackOwned(failure: Throwable) {
    try {
      if (transactions.transaction != null) transactions.rollback()
    } catch (rollbackFailure: Exception) {
      if (failure !== rollbackFailure) failure.addSuppressed(rollbackFailure)
    }
  }

  private fun outsideTransaction(action: () -> Unit) {
    val suspended = transactions.suspend()
    try {
      action()
    } finally {
      if (suspended != null) transactions.resume(suspended)
    }
  }

  private fun reportFailureOutsideTransaction(delivery: WorkerDelivery, failure: Throwable) {
    val original = rootFailure(failure)
    try {
      outsideTransaction { delivery.reportFailure(original) }
    } catch (reportingFailure: Throwable) {
      val reportingCause = rootFailure(reportingFailure)
      if (original !== reportingCause) original.addSuppressed(reportingCause)
      System.getLogger(CdiDeliveryCoordinator::class.java.name).log(System.Logger.Level.ERROR, "Failed to report worker failure", original)
    }
  }

  inner class Token(
    val delivery: WorkerDelivery,
    val owned: Boolean,
    private val cdiOwned: Boolean
  ) {
    var entered = false
    var claimed = false
    @Volatile private var afterCommit: Map<String, Any?>? = null
    @Volatile var outcome = Status.STATUS_UNKNOWN
    @Volatile private var processed = false
    var boundaryFailure: Throwable? = null
    var referenceFailure: Throwable? = null
    private var deferred: DeliveryLifecycle.Ticket? = null

    fun ownerMatches(beanIdentifier: String, invocation: Method): Boolean =
      definition.beanIdentifier() == beanIdentifier && method.name == invocation.name &&
        method.parameterTypes.contentEquals(invocation.parameterTypes)

    fun invoke(context: InvocationContext): Any? {
      entered = true
      try {
        if (cdiOwned) observeCdiOutcome()
        val result = context.proceed()
        finish(infrastructure { delivery.processResult(result) })
        return result
      } catch (failure: Exception) {
        boundaryFailure = failure
        throw failure
      } catch (failure: Error) {
        boundaryFailure = failure
        throw failure
      }
    }

    fun finish(result: Map<String, Any?>) {
      if (!delivery.autoComplete()) {
        processed = true
        return
      }
      if (delivery.completeBeforeCommit() || transactions.transaction == null) {
        infrastructure { delivery.complete(result) }
      } else afterCommit = result
      processed = true
    }

    fun prepareBorrowedTransaction() {
      if (owned || cdiOwned || transactions.transaction == null) return
      val ticket = lifecycle.defer()
      deferred = ticket
      try {
        transactions.transaction.registerSynchronization(object : Synchronization {
          override fun beforeCompletion() = Unit // The caller remains the transaction owner.
          override fun afterCompletion(status: Int) = completeBorrowed(status)
        })
      } catch (failure: Exception) {
        ticket.discard()
        throw DeliveryProcessingException(failure)
      }
    }

    private fun completeBorrowed(status: Int) {
      val failedOutcome = status != Status.STATUS_COMMITTED
      val automaticCompletion = delivery.autoComplete() && afterCommit != null
      val ticket = checkNotNull(deferred)
      if (processed && (failedOutcome || automaticCompletion)) {
        ticket.dispatch({ completeDeferred(afterCommit, status) }, true, ::reportDeferredFailure)
      } else ticket.discard()
    }

    private fun observeCdiOutcome() {
      try {
        val transaction = transactions.transaction ?: error("CDI-owned worker transaction is missing")
        transaction.registerSynchronization(object : Synchronization {
          override fun beforeCompletion() = Unit
          override fun afterCompletion(status: Int) { outcome = status }
        })
      } catch (failure: Exception) {
        throw DeliveryProcessingException(failure)
      }
    }

    private fun completeDeferred(result: Map<String, Any?>?, status: Int) {
      if (status != Status.STATUS_COMMITTED) {
        reportDeferredFailure(RollbackException("Caller-owned worker transaction did not commit"))
        return
      }
      try {
        outsideTransaction { delivery.complete(checkNotNull(result)) }
      } catch (failure: Exception) {
        reportDeferredFailure(failure)
      }
    }

    private fun reportDeferredFailure(failure: Throwable) = reportFailureOutsideTransaction(delivery, failure)

    fun completeOwned() {
      afterCommit?.let { result -> outsideTransaction { delivery.complete(result) } }
    }

    fun completeCdiOwned() {
      if (!processed) return
      if (outcome != Status.STATUS_COMMITTED) {
        throw RollbackException("CDI worker transaction did not commit (status $outcome)")
      }
      completeOwned()
    }

    fun cancelDeferred() { deferred?.cancel() }
  }

  companion object {
    private val TOKENS = ThreadLocal.withInitial { ArrayDeque<Token>() }
    private val REFERENCES = ThreadLocal.withInitial { ArrayDeque<Token>() }

    @JvmStatic
    fun claim(beanIdentifier: String, invocation: Method): Token? {
      val token = REFERENCES.get().peek()
      if (token == null) {
        REFERENCES.remove()
        return null
      }
      if (token.claimed || !token.ownerMatches(beanIdentifier, invocation)) return null
      token.claimed = true
      return token
    }

    private fun combine(failure: Throwable?, next: Throwable): Throwable {
      if (failure == null) return next
      if (failure !== next) failure.addSuppressed(next)
      return failure
    }

    private fun reportable(failure: Throwable): Exception = when (val cause = rootFailure(failure)) {
      is Error -> throw cause
      is Exception -> cause
      else -> IllegalStateException(cause)
    }

    private fun rootFailure(failure: Throwable): Throwable {
      val cause = ExceptionResolver().getCause(failure)
      return if (cause is DeliveryProcessingException) rootFailure(checkNotNull(cause.cause)) else cause
    }

    private fun <T> infrastructure(action: () -> T): T = try {
      action()
    } catch (failure: Exception) {
      throw DeliveryProcessingException(failure)
    }
  }
}
