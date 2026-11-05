package dev.bpmcrafters.processengine.worker.quarkus.deployment

import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.quarkus.JtaInMemoryIdempotencyRegistry
import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.task.*
import io.quarkus.test.QuarkusExtensionTest
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.RequestScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.interceptor.*
import jakarta.transaction.*
import java.lang.reflect.InvocationTargetException
import java.time.Duration
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import org.assertj.core.api.Assertions.*
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class TransactionCoordinationTest {
  companion object {
    @JvmField @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot { it.addClasses(
      Workers::class.java, Engine::class.java, Handle::class.java, Metadata::class.java, Audit::class.java,
      AuditRegistry::class.java, RegistryProducer::class.java, RequestProbe::class.java, RequestProbe.Companion::class.java,
      Outer::class.java, Inner::class.java, OuterInterceptor::class.java, InnerInterceptor::class.java,
      WriteSynchronization::class.java, FailingSynchronization::class.java, ReportingLogCapture::class.java, CheckedApplicationFailure::class.java) }
    fun task(id: String) = TaskInformation(id, mapOf("processInstanceId" to "process"))
  }
  @Inject lateinit var engine: Engine
  @Inject lateinit var audit: Audit
  @Inject lateinit var registry: IdempotencyRegistry
  @Inject lateinit var transactions: TransactionManager
  @Inject lateinit var workers: Workers

  @Test fun supportsAndMandatoryEstablishAnOwnedEnvelopeButOrdinaryCallsKeepCdiSemantics() {
    for (topic in listOf("supports", "mandatory")) {
      val task = task("owned-$topic")
      engine.deliver(topic, task)
      val business = engine.business[task.taskId]
      assertThat(business).isNotNull().isSameAs(audit.registered[task.taskId]).isSameAs(engine.completing[task.taskId])
      assertThat(engine.outcomes[task.taskId]).isEqualTo(Status.STATUS_COMMITTED)
      assertThat(registry.getTaskResult(task)).isNull()
    }
    val ordinary = task("ordinary-supports")
    val context = io.quarkus.arc.Arc.container().requestContext()
    context.activate()
    try { workers.supports(ordinary) } finally { context.terminate() }
    assertThat(engine.business).containsKey(ordinary.taskId)
    assertThat(engine.business[ordinary.taskId]).isNull()
    assertThat(audit.registered).doesNotContainKey(ordinary.taskId)
    assertThat(engine.results).doesNotContainKey(ordinary.taskId)
    assertThatThrownBy { workers.mandatory(task("ordinary-mandatory")) }.isInstanceOf(TransactionalException::class.java)
  }

  @Test fun requiresNewUsesOnlyItsActualWorkerTransactionForBusinessCacheAndBeforeCommitCompletion() {
    val task = task("new-before")
    engine.deliver("new-before", task)
    val business = engine.business[task.taskId]
    assertThat(business).isNotNull().isSameAs(audit.registered[task.taskId]).isSameAs(engine.completing[task.taskId])
    assertThat(audit.lookups[task.taskId]!!.filterNotNull()).hasSize(1).allSatisfy { assertThat(it).isNotSameAs(business) }
    assertThat(engine.completionCounts[task.taskId]).isEqualTo(1)
    assertThat(engine.outcomes[task.taskId]).isEqualTo(Status.STATUS_COMMITTED)
    assertThat(registry.getTaskResult(task)).isNull()
  }

  @Test fun requiresNewBeforeCommitFailureRollsBackBusinessAndCacheAtomically() {
    val task = task("new-before-failure")
    engine.offline.add(task.taskId)
    engine.deliver("new-before", task)
    assertThat(engine.outcomes[task.taskId]).isEqualTo(Status.STATUS_ROLLEDBACK)
    assertThat(engine.committedWrites).doesNotContain(task.taskId)
    assertThat(registry.getTaskResult(task)).isNull()
    assertThat(engine.failures[task.taskId]!!.reason).isEqualTo("completion offline")
  }

  @Test fun requiresNewCacheReplaySkipsBusinessAndUsesSeparateReplayTransaction() {
    val task = task("new-replay")
    engine.offline.add(task.taskId)
    engine.deliver("new-after", task)
    val business = engine.business[task.taskId]
    assertThat(registry.getTaskResult(task)).containsEntry("value", task.taskId)
    assertThat(engine.committedWrites).contains(task.taskId)
    engine.offline.remove(task.taskId)
    engine.deliver("new-after", task)
    assertThat(engine.calls[task.taskId]).isEqualTo(1)
    assertThat(engine.completionCounts[task.taskId]).isEqualTo(1)
    assertThat(engine.completing[task.taskId]).isNull()
    assertThat(audit.lookups[task.taskId]!!.filterNotNull()).hasSize(2).allSatisfy { assertThat(it).isNotSameAs(business) }
    assertThat(registry.getTaskResult(task)).isNull()
  }

  @Test fun concurrentMissesRetainRetryCacheSemanticsWithoutClaimingAtomicDuplicateSuppression() {
    val executor = Executors.newFixedThreadPool(2)
    val task = task("concurrent-miss")
    try {
      val first = CompletableFuture.runAsync({ engine.deliver("race", task) }, executor)
      val second = CompletableFuture.runAsync({ engine.deliver("race", task) }, executor)
      assertThat(engine.raceEntered.await(5, TimeUnit.SECONDS)).isTrue()
      engine.raceRelease.complete(null)
      CompletableFuture.allOf(first, second).get(5, TimeUnit.SECONDS)
      assertThat(engine.calls[task.taskId]).isEqualTo(2)
      assertThat(registry.getTaskResult(task)).containsEntry("value", task.taskId)
      assertThat(engine.completionCounts).doesNotContainKey(task.taskId)
    } finally {
      engine.raceRelease.complete(null)
      executor.shutdownNow()
    }
  }

  @Test fun callerOwnsJoinedCommitAndDeferredCallbackGetsAFreshRequestContext() {
    val task = task("ambient-after")
    transactions.begin()
    try {
      val ambient = transactions.transaction
      engine.deliver("after", task)
      assertThat(engine.business[task.taskId]).isSameAs(ambient).isSameAs(audit.registered[task.taskId])
      assertThat(transactions.transaction).isSameAs(ambient)
      assertThat(engine.results).doesNotContainKey(task.taskId)
      transactions.commit()
      await().atMost(Duration.ofSeconds(5)).untilAsserted {
        assertThat(engine.results).containsKey(task.taskId)
        assertThat(registry.getTaskResult(task)).isNull()
      }
      assertThat(engine.businessRequest[task.taskId]).isNotEqualTo(engine.completionRequest[task.taskId])
      assertThat(engine.completing[task.taskId]).isNull()
    } finally { if (transactions.transaction != null) transactions.rollback() }
  }

  @Test fun ambientRollbackReportsFailureWithoutPublishingResultOrCompletingTask() {
    val task = task("ambient-rollback")
    transactions.begin()
    try {
      engine.deliver("after", task)
      transactions.rollback()
      await().atMost(Duration.ofSeconds(5)).untilAsserted { assertThat(engine.failures).containsKey(task.taskId) }
      assertThat(engine.results).doesNotContainKey(task.taskId)
      assertThat(registry.getTaskResult(task)).isNull()
      assertThat(engine.committedWrites).doesNotContain(task.taskId)
    } finally { if (transactions.transaction != null) transactions.rollback() }
  }

  @Test fun suspendedModesDoNotCommitOrMarkTheCallersTransaction() {
    transactions.begin()
    try {
      val ambient = transactions.transaction
      val fresh = task("suspended-new")
      engine.deliver("new-before", fresh)
      assertThat(engine.business[fresh.taskId]).isNotSameAs(ambient)
      engine.deliver("not-supported", task("suspended-none"))
      assertThat(engine.business["suspended-none"]).isNull()
      engine.deliver("never", task("suspended-never"))
      assertThat(engine.failures).containsKey("suspended-never")
      assertThat(engine.business).doesNotContainKey("suspended-never")
      assertThat(transactions.transaction).isSameAs(ambient)
      assertThat(ambient.status).isEqualTo(Status.STATUS_ACTIVE)
    } finally { transactions.rollback() }
  }

  @Test fun ambientFailureFollowsJakartaPolicyAndReportsOutsideTheCallersTransaction() {
    for (topic in listOf("checked", "runtime-exempt", "runtime", "checked-rollback")) {
      val task = task("ambient-policy-$topic")
      transactions.begin()
      try {
        val ambient = transactions.transaction
        engine.deliver(topic, task)
        assertThat(transactions.transaction).isSameAs(ambient)
        val rollback = topic == "runtime" || topic == "checked-rollback"
        assertThat(ambient.status).isEqualTo(if (rollback) Status.STATUS_MARKED_ROLLBACK else Status.STATUS_ACTIVE)
        assertThat(engine.reporting[task.taskId]).isNull()
        if (topic.startsWith("checked")) assertThat(engine.errors).containsKey(task.taskId)
        else assertThat(engine.failures).containsKey(task.taskId)
        if (rollback) transactions.rollback() else transactions.commit()
      } finally { if (transactions.transaction != null) transactions.rollback() }
    }
  }

  @Test fun requiresNewCacheReplaySuspendsRatherThanJoiningTheCallersTransaction() {
    val task = task("new-ambient-replay")
    engine.offline.add(task.taskId)
    engine.deliver("new-after", task)
    engine.offline.remove(task.taskId)
    transactions.begin()
    try {
      val ambient = transactions.transaction
      engine.deliver("new-after", task)
      assertThat(transactions.transaction).isSameAs(ambient)
      assertThat(ambient.status).isEqualTo(Status.STATUS_ACTIVE)
      assertThat(audit.lookups[task.taskId]!!.filterNotNull()).allSatisfy { assertThat(it).isNotSameAs(ambient) }
      assertThat(engine.calls[task.taskId]).isEqualTo(1)
      assertThat(engine.completionCounts[task.taskId]).isEqualTo(1)
    } finally { transactions.rollback() }
    assertThat(registry.getTaskResult(task)).isNull()
  }

  @Test fun beforeCompletionInBorrowedTransactionIsNotRepeatedAtCommit() {
    val task = task("borrowed-before")
    transactions.begin()
    try {
      val ambient = transactions.transaction
      engine.deliver("mandatory", task)
      assertThat(engine.completionCounts[task.taskId]).isEqualTo(1)
      assertThat(engine.completing[task.taskId]).isSameAs(ambient)
      assertThat(engine.outcomes).doesNotContainKey(task.taskId)
      transactions.commit()
      assertThat(engine.completionCounts[task.taskId]).isEqualTo(1)
      assertThat(registry.getTaskResult(task)).isNull()
    } finally { if (transactions.transaction != null) transactions.rollback() }
  }

  @Test fun neverRejectsAmbientCacheHitsBeforeAnyLookupOrBusinessInvocation() {
    val task = task("never-cached")
    registry.register(task, mapOf("value" to "cached"))
    transactions.begin()
    try {
      val ambient = transactions.transaction
      engine.deliver("never", task)
      assertThat(engine.failures).containsKey(task.taskId)
      assertThat(audit.lookups).doesNotContainKey(task.taskId)
      assertThat(engine.calls).doesNotContainKey(task.taskId)
      assertThat(ambient.status).isEqualTo(Status.STATUS_ACTIVE)
    } finally { transactions.rollback() }
    assertThat(registry.getTaskResult(task)).containsEntry("value", "cached")
  }

  @Test fun manualRequiresNewCompletionRunsOnceInsideItsActualTransaction() {
    val task = task("manual-new")
    engine.deliver("manual-new", task)
    engine.deliver("manual-new", task)
    assertThat(engine.completing[task.taskId]).isSameAs(engine.business[task.taskId]).isSameAs(audit.registered[task.taskId])
    assertThat(engine.calls[task.taskId]).isEqualTo(1)
    assertThat(engine.completionCounts[task.taskId]).isEqualTo(1)
    assertThat(registry.getTaskResult(task)).containsEntry("value", task.taskId)
  }

  @Test fun honorsCheckedRuntimeAndExplicitRollbackPolicies() {
    for (topic in listOf("checked", "checked-exempt", "runtime-exempt", "checked-wrapper")) {
      val task = task("policy-$topic")
      engine.deliver(topic, task)
      assertThat(engine.outcomes[task.taskId]).isEqualTo(Status.STATUS_COMMITTED)
      assertThat(engine.committedWrites).contains(task.taskId)
    }
    for (topic in listOf("checked-rollback", "runtime", "rollback-only", "synchronization-failure")) {
      val task = task("policy-$topic")
      engine.deliver(topic, task)
      assertThat(engine.outcomes[task.taskId]).isEqualTo(Status.STATUS_ROLLEDBACK)
      assertThat(engine.committedWrites).doesNotContain(task.taskId)
      assertThat(registry.getTaskResult(task)).isNull()
    }
    assertThat(engine.errors["policy-checked"]!!.errorCode).isEqualTo("checked-code")
    assertThat(engine.failures["policy-checked-wrapper"]!!.reason).isEqualTo("wrapped runtime")
  }

  @Test fun ordinaryCdiUnwrapsInvocationTargetExceptionBeforeApplyingRollbackPolicy() {
    val task = task("ordinary-arc-reflection")
    val context = io.quarkus.arc.Arc.container().requestContext()
    context.activate()
    transactions.begin()
    try {
      val ambient = transactions.transaction
      assertThatThrownBy { workers.arcReflection(task) }.isInstanceOf(IllegalStateException::class.java).hasMessage("wrapped runtime")
      assertThat(transactions.transaction).isSameAs(ambient)
      assertThat(ambient.status).isEqualTo(Status.STATUS_MARKED_ROLLBACK)
      transactions.rollback()
      assertThat(engine.outcomes[task.taskId]).isEqualTo(Status.STATUS_ROLLEDBACK)
      assertThat(engine.failures).doesNotContainKey(task.taskId)
      assertThat(audit.registered).doesNotContainKey(task.taskId)
    } finally {
      if (transactions.transaction != null) transactions.rollback()
      context.terminate()
    }
  }

  @Test fun keepsInnerTransactionResultAndAllInterceptorsIncludingOuterFailureAfterCommit() {
    val normal = task("wrapped-normal")
    engine.deliver("wrapped", normal)
    assertThat(engine.results[normal.taskId]).containsEntry("inner", true).doesNotContainKeys("outer", "outer-mutated")
    val failed = task("wrapped-outer-failure")
    engine.deliver("wrapped", failed)
    assertThat(engine.outcomes[failed.taskId]).isEqualTo(Status.STATUS_COMMITTED)
    assertThat(engine.results[failed.taskId]).containsEntry("inner", true).doesNotContainKeys("outer", "outer-mutated")
    assertThat(engine.failures[failed.taskId]!!.reason).isEqualTo("outer failed after commit")
    assertThat(engine.completionCounts[failed.taskId]).isEqualTo(1)
    val context = io.quarkus.arc.Arc.container().requestContext()
    context.activate()
    try { assertThat(workers.wrapped(task("wrapped-ordinary"))).containsExactly(entry("outer", true)) }
    finally { context.terminate() }
    assertThat(engine.results).doesNotContainKey("wrapped-ordinary")
  }

  @Test fun nestedDeliveriesDoNotCaptureOrdinaryCallsOrLoseTheOuterToken() {
    engine.deliver("root", task("root-task"))
    assertThat(engine.completionCounts).containsEntry("root-task", 1).containsEntry("root-task-child", 1)
    assertThat(engine.business["root-task"]).isSameAs(engine.business["root-task-child"])
    assertThat(audit.registered).doesNotContainKey("root-task-ordinary")
    assertThat(engine.completionCounts).doesNotContainKey("root-task-ordinary")
  }
  @Test fun ordinarySameMethodCallFromAnOuterInterceptorCannotClaimTheDeliveryToken() {
    engine.deliver("wrapped", task("wrapped-precall"))
    assertThat(engine.calls).containsEntry("wrapped-precall", 1).containsEntry("wrapped-precall-ordinary-before", 1)
    assertThat(audit.registered).containsKey("wrapped-precall").doesNotContainKey("wrapped-precall-ordinary-before")
    assertThat(engine.results).containsKey("wrapped-precall").doesNotContainKey("wrapped-precall-ordinary-before")
  }
  @Test fun outerShortCircuitCannotMasqueradeAsATransactionBoundWorkerResult() {
    engine.deliver("wrapped", task("wrapped-short-circuit"))
    assertThat(engine.calls).doesNotContainKey("wrapped-short-circuit")
    assertThat(engine.results).doesNotContainKey("wrapped-short-circuit")
    assertThat(engine.failures["wrapped-short-circuit"]!!.reason).contains("missed its delivery interceptor")
    assertThat(workers.wrapped(task("ordinary-short-circuit"))).containsExactly(entry("outer", true))
  }
  @Test fun outerRecoveryDoesNotReplaceAnInnerFailedOutcomeButOrdinaryCallsStillRecover() {
    engine.deliver("wrapped", task("wrapped-recover"))
    assertThat(engine.outcomes["wrapped-recover"]).isEqualTo(Status.STATUS_COMMITTED)
    assertThat(engine.errors["wrapped-recover"]!!.errorCode).isEqualTo("checked-code")
    assertThat(engine.results).doesNotContainKey("wrapped-recover")
    val context = io.quarkus.arc.Arc.container().requestContext()
    context.activate()
    try {
      assertThat(workers.wrapped(task("ordinary-recover"))).containsExactly(entry("outer", true))
      assertThat(engine.errors).doesNotContainKey("ordinary-recover")
    } finally { context.terminate() }
  }
  @Test fun requiresNewCommitFailureRecoveredByOuterInterceptorStillReportsOneFailure() {
    val task = task("commit-failure-recover")
    engine.deliver("swallowed-commit", task)
    assertThat(engine.calls[task.taskId]).isEqualTo(1)
    assertThat(audit.registered[task.taskId]).isSameAs(engine.business[task.taskId])
    assertThat(engine.outcomes[task.taskId]).isEqualTo(Status.STATUS_ROLLEDBACK)
    assertThat(engine.committedWrites).doesNotContain(task.taskId)
    assertThat(registry.getTaskResult(task)).isNull()
    assertThat(engine.results).doesNotContainKey(task.taskId)
    assertThat(engine.completionCounts.getOrDefault(task.taskId, 0)).isZero()
    assertThat(engine.failureAttempts.getOrDefault(task.taskId, 0)).isEqualTo(1)
  }
  @Test fun requiresNewManualCommitFailureRecoveredByOuterInterceptorStillReportsOneFailure() {
    assertRecoveredCommitFailure("swallowed-manual-commit", 0)
  }
  @Test fun requiresNewBeforeCommitFailureRecoveredByOuterInterceptorStillReportsOneFailure() {
    assertRecoveredCommitFailure("swallowed-before-commit", 1)
  }
  private fun assertRecoveredCommitFailure(topic: String, expectedCompletions: Int) {
    val task = task("$topic-recover")
    engine.deliver(topic, task)
    assertThat(engine.calls[task.taskId]).isEqualTo(1)
    assertThat(audit.registered[task.taskId]).isSameAs(engine.business[task.taskId])
    assertThat(engine.outcomes[task.taskId]).isEqualTo(Status.STATUS_ROLLEDBACK)
    assertThat(engine.committedWrites).doesNotContain(task.taskId)
    assertThat(registry.getTaskResult(task)).isNull()
    assertThat(engine.completionCounts.getOrDefault(task.taskId, 0)).isEqualTo(expectedCompletions)
    if (expectedCompletions == 0) assertThat(engine.results).doesNotContainKey(task.taskId)
    else assertThat(engine.completing[task.taskId]).isSameAs(engine.business[task.taskId])
    assertThat(engine.failureAttempts.getOrDefault(task.taskId, 0)).isEqualTo(1)
  }
  @Test fun manualRequiredAmbientRollbackReportsOneFailure() { assertManualRollback("manual-required") }
  @Test fun manualMandatoryAmbientRollbackReportsOneFailure() { assertManualRollback("manual-mandatory") }
  private fun assertManualRollback(topic: String) {
    val task = task("rollback-$topic")
    transactions.begin()
    try {
      val ambient = transactions.transaction
      engine.deliver(topic, task)
      assertThat(transactions.transaction).isSameAs(ambient)
      assertThat(audit.registered[task.taskId]).isSameAs(ambient)
      assertThat(engine.failureAttempts.getOrDefault(task.taskId, 0)).isZero()
      transactions.rollback()
      await().atMost(Duration.ofSeconds(5)).untilAsserted { assertThat(engine.failureAttempts.getOrDefault(task.taskId, 0)).isEqualTo(1) }
      assertThat(registry.getTaskResult(task)).isNull()
      assertThat(engine.committedWrites).doesNotContain(task.taskId)
      assertThat(engine.results).doesNotContainKey(task.taskId)
      assertThat(engine.completionCounts.getOrDefault(task.taskId, 0)).isZero()
    } finally { if (transactions.transaction != null) transactions.rollback() }
  }
  @Test fun manualInvocationFailureIsNotReportedAgainAtAmbientRollback() {
    for (topic in listOf("manual-required", "manual-mandatory")) {
      val task = task("manual-invocation-failure-$topic")
      transactions.begin()
      try {
        val ambient = transactions.transaction
        engine.deliver(topic, task)
        assertThat(transactions.transaction).isSameAs(ambient)
        assertThat(ambient.status).isEqualTo(Status.STATUS_MARKED_ROLLBACK)
        assertThat(engine.failureAttempts.getOrDefault(task.taskId, 0)).isEqualTo(1)
        transactions.rollback()
        assertThat(engine.failureAttempts.getOrDefault(task.taskId, 0)).isEqualTo(1)
        assertThat(registry.getTaskResult(task)).isNull()
        assertThat(engine.completionCounts.getOrDefault(task.taskId, 0)).isZero()
      } finally { if (transactions.transaction != null) transactions.rollback() }
    }
  }
  @Test fun bpmnReportingFailureHasOneTerminalOwnerOutsideAmbientTransaction() {
    val task = task("bpmn-reporting-failure")
    val reportingFailure = IllegalStateException("business error reporting offline")
    engine.errorReportingFailures[task.taskId] = reportingFailure
    transactions.begin()
    try {
      ReportingLogCapture().use { logs ->
        val ambient = transactions.transaction
        val escaped = catchThrowable { engine.deliver("checked", task) }
        assertThat(engine.errorAttempts.getOrDefault(task.taskId, 0)).isEqualTo(1)
        assertThat(engine.failureAttempts.getOrDefault(task.taskId, 0)).isZero()
        assertThat(engine.errors[task.taskId]!!.errorCode).isEqualTo("checked-code")
        assertThat(engine.reportingHistory[task.taskId]).hasSize(1).allSatisfy { assertThat(it).isNull() }
        assertThat(transactions.transaction).isSameAs(ambient)
        assertThat(ambient.status).isEqualTo(Status.STATUS_ACTIVE)
        assertThat(logs.failuresIncluding(escaped)).contains(engine.workerFailures[task.taskId], reportingFailure)
      }
    } finally { transactions.rollback() }
  }
  @Test fun technicalReportingFailureHasOneTerminalOwnerOutsideAmbientTransaction() {
    val task = task("technical-reporting-failure")
    val reportingFailure = IllegalStateException("technical failure reporting offline")
    engine.technicalReportingFailures[task.taskId] = reportingFailure
    transactions.begin()
    try {
      ReportingLogCapture().use { logs ->
        val ambient = transactions.transaction
        val escaped = catchThrowable { engine.deliver("runtime-exempt", task) }
        assertThat(engine.failureAttempts.getOrDefault(task.taskId, 0)).isEqualTo(1)
        assertThat(engine.errorAttempts.getOrDefault(task.taskId, 0)).isZero()
        assertThat(engine.failures[task.taskId]!!.reason).isEqualTo("runtime exempt")
        assertThat(engine.reportingHistory[task.taskId]).hasSize(1).allSatisfy { assertThat(it).isNull() }
        assertThat(transactions.transaction).isSameAs(ambient)
        assertThat(ambient.status).isEqualTo(Status.STATUS_ACTIVE)
        assertThat(logs.failuresIncluding(escaped)).contains(engine.workerFailures[task.taskId], reportingFailure)
      }
    } finally { transactions.rollback() }
  }
  @Test fun deferredRollbackReportingFailureHasOneTerminalOwner() {
    val task = task("deferred-rollback-reporting-failure")
    val reportingFailure = IllegalStateException("deferred failure reporting offline")
    engine.technicalReportingFailures[task.taskId] = reportingFailure
    transactions.begin()
    try {
      ReportingLogCapture().use { logs ->
        val ambient = transactions.transaction
        engine.deliver("after", task)
        assertThat(transactions.transaction).isSameAs(ambient)
        assertThat(ambient.status).isEqualTo(Status.STATUS_ACTIVE)
        assertThat(engine.failureAttempts.getOrDefault(task.taskId, 0)).isZero()
        transactions.rollback()
        await().atMost(Duration.ofSeconds(5)).untilAsserted {
          assertThat(engine.failureAttempts.getOrDefault(task.taskId, 0)).isPositive()
          assertThat(logs.failuresIncluding(null)).contains(reportingFailure).anyMatch { it is RollbackException }
        }
        assertThat(engine.failureAttempts.getOrDefault(task.taskId, 0)).isEqualTo(1)
        assertThat(engine.errorAttempts.getOrDefault(task.taskId, 0)).isZero()
        assertThat(engine.failures[task.taskId]!!.errorDetails).contains(RollbackException::class.java.name)
        assertThat(engine.reportingHistory[task.taskId]).hasSize(1).allSatisfy { assertThat(it).isNull() }
        assertThat(transactions.transaction).isNull()
        assertThat(registry.getTaskResult(task)).isNull()
        assertThat(engine.completionCounts.getOrDefault(task.taskId, 0)).isZero()
      }
    } finally { if (transactions.transaction != null) transactions.rollback() }
  }

  @ApplicationScoped
  class Workers {
    @Inject lateinit var engine: Engine
    @Inject lateinit var transactions: TransactionManager
    @Transactional(Transactional.TxType.SUPPORTS) @ProcessEngineWorker(topic = "supports", completion = ProcessEngineWorker.Completion.BEFORE_COMMIT)
    @Throws(Exception::class)
    fun supports(task: TaskInformation): Map<String, Any?> = engine.write(task)
    @Transactional(Transactional.TxType.MANDATORY) @ProcessEngineWorker(topic = "mandatory", completion = ProcessEngineWorker.Completion.BEFORE_COMMIT)
    @Throws(Exception::class)
    fun mandatory(task: TaskInformation): Map<String, Any?> = engine.write(task)
    @Transactional(Transactional.TxType.REQUIRES_NEW) @ProcessEngineWorker(topic = "new-before", completion = ProcessEngineWorker.Completion.BEFORE_COMMIT)
    @Throws(Exception::class)
    fun newBefore(task: TaskInformation): Map<String, Any?> = engine.write(task)
    @Transactional(Transactional.TxType.REQUIRES_NEW) @ProcessEngineWorker(topic = "new-after")
    @Throws(Exception::class)
    fun newAfter(task: TaskInformation): Map<String, Any?> = engine.write(task)
    @Transactional(Transactional.TxType.REQUIRES_NEW) @ProcessEngineWorker(topic = "race", autoComplete = false)
    @Throws(Exception::class)
    fun race(task: TaskInformation): Map<String, Any?> {
      engine.raceEntered.countDown()
      engine.raceRelease.get(30, TimeUnit.SECONDS)
      return engine.write(task)
    }
    @Transactional(Transactional.TxType.REQUIRES_NEW) @ProcessEngineWorker(topic = "manual-new", autoComplete = false, completion = ProcessEngineWorker.Completion.BEFORE_COMMIT)
    @Throws(Exception::class)
    fun manualNew(task: TaskInformation, completion: ServiceTaskCompletionApi): Map<String, Any?> {
      val result = engine.write(task)
      completion.completeTask(CompleteTaskCmd(task.taskId, mapOf("manual" to true))).join()
      return result
    }
    @Transactional @ProcessEngineWorker(topic = "after")
    @Throws(Exception::class)
    fun after(task: TaskInformation): Map<String, Any?> = engine.write(task)
    @Transactional(Transactional.TxType.NOT_SUPPORTED) @ProcessEngineWorker(topic = "not-supported")
    @Throws(Exception::class)
    fun notSupported(task: TaskInformation): Map<String, Any?> = engine.write(task)
    @Transactional(Transactional.TxType.NEVER) @ProcessEngineWorker(topic = "never")
    @Throws(Exception::class)
    fun never(task: TaskInformation): Map<String, Any?> = engine.write(task)
    @Transactional @ProcessEngineWorker(topic = "checked")
    @Throws(Exception::class)
    fun checked(task: TaskInformation) {
      engine.write(task)
      val failure = checkedError()
      engine.workerFailures[task.taskId] = failure
      throw failure
    }
    @Transactional(rollbackOn = [BpmnErrorOccurred::class]) @ProcessEngineWorker(topic = "checked-rollback")
    @Throws(Exception::class)
    fun checkedRollback(task: TaskInformation) { engine.write(task); throw checkedError() }
    @Transactional(rollbackOn = [Exception::class], dontRollbackOn = [BpmnErrorOccurred::class]) @ProcessEngineWorker(topic = "checked-exempt")
    @Throws(Exception::class)
    fun checkedExempt(task: TaskInformation) { engine.write(task); throw checkedError() }
    @Transactional(rollbackOn = [RuntimeException::class], dontRollbackOn = [CheckedApplicationFailure::class])
    @ProcessEngineWorker(topic = "checked-wrapper")
    @Throws(Exception::class)
    fun checkedApplicationWrapper(task: TaskInformation) {
      engine.write(task)
      throw CheckedApplicationFailure(IllegalStateException("wrapped runtime"))
    }
    @Transactional(rollbackOn = [RuntimeException::class], dontRollbackOn = [InvocationTargetException::class])
    @ProcessEngineWorker(topic = "arc-reflection")
    @Throws(Exception::class)
    fun arcReflection(task: TaskInformation) {
      engine.write(task)
      throw InvocationTargetException(IllegalStateException("wrapped runtime"))
    }
    @Transactional @ProcessEngineWorker(topic = "runtime")
    @Throws(Exception::class)
    fun runtime(task: TaskInformation) { engine.write(task); throw IllegalArgumentException("runtime") }
    @Transactional(dontRollbackOn = [IllegalArgumentException::class]) @ProcessEngineWorker(topic = "runtime-exempt")
    @Throws(Exception::class)
    fun runtimeExempt(task: TaskInformation) {
      engine.write(task)
      val failure = IllegalArgumentException("runtime exempt")
      engine.workerFailures[task.taskId] = failure
      throw failure
    }
    @Transactional @ProcessEngineWorker(topic = "rollback-only")
    @Throws(Exception::class)
    fun rollbackOnly(task: TaskInformation): Map<String, Any?> {
      val result = engine.write(task)
      transactions.setRollbackOnly()
      return result
    }
    @Transactional @ProcessEngineWorker(topic = "synchronization-failure")
    @Throws(Exception::class)
    fun synchronizationFailure(task: TaskInformation): Map<String, Any?> = failBeforeCommit(task)
    @Outer @Inner @Transactional(Transactional.TxType.REQUIRES_NEW) @ProcessEngineWorker(topic = "swallowed-commit")
    @Throws(Exception::class)
    fun swallowedCommit(task: TaskInformation): Map<String, Any?> = failBeforeCommit(task)
    @Outer @Inner @Transactional(Transactional.TxType.REQUIRES_NEW) @ProcessEngineWorker(topic = "swallowed-manual-commit", autoComplete = false)
    @Throws(Exception::class)
    fun swallowedManualCommit(task: TaskInformation): Map<String, Any?> = failBeforeCommit(task)
    @Outer @Inner @Transactional(Transactional.TxType.REQUIRES_NEW)
    @ProcessEngineWorker(topic = "swallowed-before-commit", completion = ProcessEngineWorker.Completion.BEFORE_COMMIT)
    @Throws(Exception::class)
    fun swallowedBeforeCommit(task: TaskInformation): Map<String, Any?> = failBeforeCommit(task)
    @Throws(Exception::class)
    private fun failBeforeCommit(task: TaskInformation): Map<String, Any?> {
      val result = engine.write(task)
      transactions.transaction.registerSynchronization(FailingSynchronization())
      return result
    }
    @Transactional @ProcessEngineWorker(topic = "manual-required", autoComplete = false)
    @Throws(Exception::class)
    fun manualRequired(task: TaskInformation): Map<String, Any?> = manualWork(task)
    @Transactional(Transactional.TxType.MANDATORY) @ProcessEngineWorker(topic = "manual-mandatory", autoComplete = false)
    @Throws(Exception::class)
    fun manualMandatory(task: TaskInformation): Map<String, Any?> = manualWork(task)
    @Throws(Exception::class)
    private fun manualWork(task: TaskInformation): Map<String, Any?> {
      val result = engine.write(task)
      if (task.taskId.startsWith("manual-invocation-failure")) throw IllegalArgumentException("manual invocation failed")
      return result
    }
    @Outer @Inner @Transactional(Transactional.TxType.REQUIRES_NEW) @ProcessEngineWorker(topic = "wrapped")
    @Throws(Exception::class)
    fun wrapped(task: TaskInformation): Map<String, Any?> {
      val result = engine.write(task)
      if (task.taskId.endsWith("recover")) throw checkedError()
      return result
    }
    @Transactional @ProcessEngineWorker(topic = "root", completion = ProcessEngineWorker.Completion.BEFORE_COMMIT)
    @Throws(Exception::class)
    fun root(task: TaskInformation): Map<String, Any?> {
      val result = engine.write(task)
      leaf(TaskInformation(task.taskId + "-ordinary", mapOf("processInstanceId" to "process")))
      engine.deliver("leaf", TaskInformation(task.taskId + "-child", mapOf("processInstanceId" to "process")))
      return result
    }
    @Transactional @ProcessEngineWorker(topic = "leaf", completion = ProcessEngineWorker.Completion.BEFORE_COMMIT)
    @Throws(Exception::class)
    fun leaf(task: TaskInformation): Map<String, Any?> = engine.write(task)
    private fun checkedError() = BpmnErrorOccurred("checked", "checked-code", emptyMap())
  }

  @InterceptorBinding @Retention(AnnotationRetention.RUNTIME) @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
  annotation class Outer
  @InterceptorBinding @Retention(AnnotationRetention.RUNTIME) @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
  annotation class Inner
  @Interceptor @Outer @Priority(100)
  class OuterInterceptor {
    @Inject lateinit var workers: Workers
    @AroundInvoke
    @Throws(Exception::class)
    @Suppress("UNCHECKED_CAST")
    fun intercept(context: InvocationContext): Any? {
      val task = context.parameters[0] as TaskInformation
      if (task.taskId.endsWith("short-circuit")) return mapOf("outer" to true)
      if (task.taskId.endsWith("precall")) workers.wrapped(TaskInformation(task.taskId + "-ordinary-before", mapOf("processInstanceId" to "process")))
      val result = try { context.proceed() as MutableMap<String, Any?> }
      catch (failure: Exception) {
        if (task.taskId.endsWith("recover")) return mapOf("outer" to true)
        throw failure
      }
      result["outer-mutated"] = true
      if ((context.parameters[0] as TaskInformation).taskId.endsWith("outer-failure")) throw IllegalStateException("outer failed after commit")
      return mapOf("outer" to true)
    }
  }
  @Interceptor @Inner @Priority(Interceptor.Priority.APPLICATION)
  class InnerInterceptor {
    @AroundInvoke
    @Throws(Exception::class)
    @Suppress("UNCHECKED_CAST")
    fun intercept(context: InvocationContext): Any? {
      val result = HashMap(context.proceed() as Map<String, Any?>)
      result["inner"] = true
      return result
    }
  }
  @RequestScoped
  class RequestProbe {
    companion object { private val IDS = AtomicLong() }
    private val id = IDS.incrementAndGet().toString()
    fun id() = id
  }
  class Handle : TaskSubscription
  class Metadata : MetaInfo
  class CheckedApplicationFailure(cause: Throwable) : Exception("wrapped runtime", cause)
  class FailingSynchronization : Synchronization {
    override fun beforeCompletion() { throw IllegalStateException("before completion failed") }
    override fun afterCompletion(status: Int) {}
  }
  data class WriteSynchronization(val engine: Engine, val taskId: String) : Synchronization {
    override fun beforeCompletion() {}
    override fun afterCompletion(status: Int) {
      engine.outcomes[taskId] = status
      if (status == Status.STATUS_COMMITTED) engine.committedWrites.add(taskId)
    }
  }
  class ReportingLogCapture : Handler(), AutoCloseable {
    private val root = Logger.getLogger("")
    private val failures = CopyOnWriteArrayList<Throwable>()
    init { level = Level.ALL; root.addHandler(this) }
    override fun publish(record: LogRecord) { record.thrown?.let { failures.add(it) } }
    fun failuresIncluding(escaped: Throwable?): Set<Throwable> {
      val observed = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
      val pending = ArrayDeque(failures)
      if (escaped != null) pending.add(escaped)
      while (pending.isNotEmpty()) {
        val failure = pending.removeFirst()
        if (!observed.add(failure)) continue
        failure.cause?.let { pending.add(it) }
        pending.addAll(failure.suppressed)
      }
      return observed
    }
    override fun flush() {}
    override fun close() { root.removeHandler(this) }
  }
  @Singleton
  class Audit {
    val registered: MutableMap<String, Transaction?> = Collections.synchronizedMap(HashMap())
    val lookups = ConcurrentHashMap<String, MutableList<Transaction?>>()
  }
  class AuditRegistry(private val storage: IdempotencyRegistry, private val transactions: TransactionManager, private val audit: Audit) : IdempotencyRegistry {
    override fun register(task: TaskInformation, result: Map<String, Any?>) {
      audit.registered[task.taskId] = current()
      storage.register(task, result)
    }
    override fun getTaskResult(task: TaskInformation): Map<String, Any?>? {
      audit.lookups.computeIfAbsent(task.taskId) { CopyOnWriteArrayList() }.add(current())
      return storage.getTaskResult(task)
    }
    override fun removeTaskResult(id: String) { storage.removeTaskResult(id) }
    override fun removeAllTaskResults(id: String) { storage.removeAllTaskResults(id) }
    private fun current(): Transaction? = try { transactions.transaction }
    catch (failure: SystemException) { throw IllegalStateException(failure) }
  }
  @Singleton
  class RegistryProducer {
    @Produces @Singleton
    fun registry(synchronization: TransactionSynchronizationRegistry, transactions: TransactionManager, audit: Audit): IdempotencyRegistry =
      AuditRegistry(JtaInMemoryIdempotencyRegistry(synchronization), transactions, audit)
  }
  @Singleton
  class Engine : TaskSubscriptionApi, ServiceTaskCompletionApi {
    @Inject lateinit var transactions: TransactionManager
    @Inject lateinit var request: RequestProbe
    val commands = ConcurrentHashMap<String, SubscribeForTaskCmd>()
    val business: MutableMap<String, Transaction?> = Collections.synchronizedMap(HashMap())
    val completing: MutableMap<String, Transaction?> = Collections.synchronizedMap(HashMap())
    val reporting: MutableMap<String, Transaction?> = Collections.synchronizedMap(HashMap())
    val reportingHistory = ConcurrentHashMap<String, MutableList<Transaction?>>()
    val errorAttempts = ConcurrentHashMap<String, Int>()
    val failureAttempts = ConcurrentHashMap<String, Int>()
    val errorReportingFailures = ConcurrentHashMap<String, RuntimeException>()
    val technicalReportingFailures = ConcurrentHashMap<String, RuntimeException>()
    val workerFailures = ConcurrentHashMap<String, Throwable>()
    val outcomes = ConcurrentHashMap<String, Int>()
    val calls = ConcurrentHashMap<String, Int>()
    val completionCounts = ConcurrentHashMap<String, Int>()
    val businessRequest = ConcurrentHashMap<String, String>()
    val completionRequest = ConcurrentHashMap<String, String>()
    val results = ConcurrentHashMap<String, Map<String, Any?>>()
    val failures = ConcurrentHashMap<String, FailTaskCmd>()
    val errors = ConcurrentHashMap<String, CompleteTaskByErrorCmd>()
    val committedWrites: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val offline: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val raceEntered = CountDownLatch(2)
    val raceRelease = CompletableFuture<Void>()
    fun deliver(topic: String, task: TaskInformation) { commands[topic]!!.action.accept(task, emptyMap()) }
    @Throws(Exception::class)
    fun write(task: TaskInformation): Map<String, Any?> {
      val id = task.taskId
      val transaction = transactions.transaction
      business[id] = transaction
      businessRequest[id] = request.id()
      calls.merge(id, 1) { first, second -> first + second }
      if (transaction == null) committedWrites.add(id) else transaction.registerSynchronization(WriteSynchronization(this, id))
      return mapOf("value" to id)
    }
    override fun subscribeForTask(command: SubscribeForTaskCmd): CompletableFuture<TaskSubscription> {
      commands[requireNotNull(command.taskDescriptionKey)] = command
      return CompletableFuture.completedFuture(Handle())
    }
    override fun unsubscribe(command: UnsubscribeFromTaskCmd): CompletableFuture<Empty> = done()
    override fun getSupportedRestrictions(): Set<String> = emptySet()
    override fun meta(instance: MetaInfoAware): MetaInfo = Metadata()
    override fun completeTask(command: CompleteTaskCmd): CompletableFuture<Empty> = try {
      completing[command.taskId] = transactions.transaction
      completionRequest[command.taskId] = request.id()
      if (command.taskId in offline) CompletableFuture.failedFuture(IllegalStateException("completion offline"))
      else {
        completionCounts.merge(command.taskId, 1) { first, second -> first + second }
        results[command.taskId] = command.get()
        done()
      }
    } catch (failure: Exception) { CompletableFuture.failedFuture(failure) }
    override fun completeTaskByError(command: CompleteTaskByErrorCmd): CompletableFuture<Empty> {
      recordReporting(command.taskId)
      errorAttempts.merge(command.taskId, 1) { first, second -> first + second }
      errors[command.taskId] = command
      errorReportingFailures[command.taskId]?.let { throw it }
      return done()
    }
    override fun failTask(command: FailTaskCmd): CompletableFuture<Empty> {
      recordReporting(command.taskId)
      failureAttempts.merge(command.taskId, 1) { first, second -> first + second }
      failures[command.taskId] = command
      technicalReportingFailures[command.taskId]?.let { throw it }
      return done()
    }
    private fun recordReporting(id: String) {
      try {
        val transaction = transactions.transaction
        reporting[id] = transaction
        reportingHistory.computeIfAbsent(id) { CopyOnWriteArrayList() }.add(transaction)
      } catch (failure: SystemException) { throw IllegalStateException(failure) }
    }
    private fun done(): CompletableFuture<Empty> = CompletableFuture.completedFuture(Empty)
  }
}
