package dev.bpmcrafters.processengine.worker.acceptance

import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred
import dev.bpmcrafters.processengine.worker.FailJobException
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import dev.bpmcrafters.processengine.worker.registrar.VariableConverter
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskInformation
import io.quarkus.runtime.annotations.RegisterForReflection
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.transaction.Synchronization
import jakarta.transaction.TransactionSynchronizationRegistry
import jakarta.transaction.Transactional
import java.time.Duration
import java.util.Locale

@ApplicationScoped
class AcceptanceWorkers {
  @field:Inject lateinit var entityManager: EntityManager
  @field:Inject lateinit var engine: EngineFixture
  @field:Inject lateinit var synchronizations: TransactionSynchronizationRegistry

  @ProcessEngineWorker(topic = "required-before", completion = ProcessEngineWorker.Completion.BEFORE_COMMIT)
  @Transactional
  fun requiredBefore(task: TaskInformation): Map<String, Any?> = business(task)

  @ProcessEngineWorker(topic = "required-after", completion = ProcessEngineWorker.Completion.AFTER_COMMIT)
  @Transactional
  fun requiredAfter(task: TaskInformation): Map<String, Any?> = business(task)

  @ProcessEngineWorker(topic = "new-before", completion = ProcessEngineWorker.Completion.BEFORE_COMMIT)
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  fun newBefore(task: TaskInformation): Map<String, Any?> = business(task)

  @ProcessEngineWorker(topic = "new-after", completion = ProcessEngineWorker.Completion.AFTER_COMMIT)
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  fun newAfter(task: TaskInformation): Map<String, Any?> = business(task)

  @ProcessEngineWorker(topic = "mandatory", completion = ProcessEngineWorker.Completion.AFTER_COMMIT)
  @Transactional(Transactional.TxType.MANDATORY)
  fun mandatory(task: TaskInformation): Map<String, Any?> = business(task)

  @ProcessEngineWorker(topic = "supports", completion = ProcessEngineWorker.Completion.AFTER_COMMIT)
  @Transactional(Transactional.TxType.SUPPORTS)
  fun supports(task: TaskInformation): Map<String, Any?> = business(task)

  @ProcessEngineWorker(topic = "runtime-failure", completion = ProcessEngineWorker.Completion.AFTER_COMMIT)
  @Transactional
  fun runtimeFailure(task: TaskInformation): Map<String, Any?> {
    business(task)
    throw IllegalStateException("Failure after persisting business work")
  }

  @ProcessEngineWorker(topic = "flush-failure", completion = ProcessEngineWorker.Completion.AFTER_COMMIT)
  @Transactional
  fun flushFailure(task: TaskInformation): Map<String, Any?> {
    val result = business(task, "reject-at-flush")
    engine.workerReturned(task.taskId)
    return result // PostgreSQL rejects the queued insert on flush, not in this method.
  }

  @ProcessEngineWorker(topic = "nonserializable-result", completion = ProcessEngineWorker.Completion.AFTER_COMMIT)
  @Transactional
  fun nonserializableResult(task: TaskInformation): Map<String, Any?> {
    business(task)
    return mapOf("nested" to mapOf("notSerializable" to Any()))
  }

  @ProcessEngineWorker(topic = "manual", autoComplete = false)
  @Transactional
  fun manual(task: TaskInformation, completion: ServiceTaskCompletionApi) {
    business(task)
    completion.completeTask(CompleteTaskCmd(task.taskId) { mapOf("manual" to true) }).join()
  }

  @ProcessEngineWorker(topic = "typed")
  fun typed(task: TaskInformation, @Variable("payload") payload: WorkerPayload,
    @Variable("state") state: WorkerPayload.State,
    @Variable(value = "label", converter = UpperCase::class) label: String): Map<String, Any?> {
    engine.invoked(task.taskId)
    return mapOf("name" to payload.name, "amount" to payload.details.amount,
      "state" to state.name, "nestedState" to payload.details.state.name, "label" to label)
  }

  @ProcessEngineWorker(topic = "business-error")
  fun businessError() {
    throw BpmnErrorOccurred("Expected business error", "fixture-error", mapOf("reason" to "expected"))
  }

  @ProcessEngineWorker(topic = "retry")
  fun retry() {
    throw FailJobException("Expected retry", null, 4, Duration.ofSeconds(30))
  }

  private fun business(task: TaskInformation, value: String = "business-result"): Map<String, Any?> {
    val id = task.taskId
    engine.invoked(id)
    engine.workerTransaction(id, synchronizations.transactionKey)
    synchronizations.registerInterposedSynchronization(object : Synchronization {
      override fun beforeCompletion() = Unit
      override fun afterCompletion(status: Int) = engine.transactionEnded(id, status)
    })
    entityManager.persist(BusinessEntry(id, value))
    return mapOf("value" to "business-result")
  }

  @RegisterForReflection
  class UpperCase : VariableConverter {
    override fun <T : Any> mapToType(value: Any?, type: Class<T>): T =
      type.cast((value as String).uppercase(Locale.ROOT))
  }
}
