package dev.bpmcrafters.processengine.worker.quarkus.deployment.workers;

import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred;
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengineapi.task.TaskInformation;
import jakarta.inject.Inject;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Transactional workers probing the transaction state.
 */
public class TransactionalWorker {

  @Inject
  TransactionSynchronizationRegistry synchronizationRegistry;

  private final List<Integer> statusAtInvocation = new CopyOnWriteArrayList<>();
  private final List<Integer> completionStatus = new CopyOnWriteArrayList<>();
  private final List<String> invokedTasks = new CopyOnWriteArrayList<>();

  @Transactional
  @ProcessEngineWorker(topic = "tx-after-commit")
  public Map<String, Object> afterCommit(TaskInformation taskInformation) {
    probe(taskInformation);
    return Map.of("tx", "after");
  }

  @Transactional
  @ProcessEngineWorker(topic = "tx-before-commit", completion = ProcessEngineWorker.Completion.BEFORE_COMMIT)
  public Map<String, Object> beforeCommit(TaskInformation taskInformation) {
    probe(taskInformation);
    return Map.of("tx", "before");
  }

  @Transactional
  @ProcessEngineWorker(topic = "tx-bpmn-error")
  public void bpmnError(TaskInformation taskInformation) throws BpmnErrorOccurred {
    probe(taskInformation);
    throw new BpmnErrorOccurred("business error in tx", "TX_ERROR", Map.of());
  }

  @Transactional
  @ProcessEngineWorker(topic = "tx-runtime-error")
  public void runtimeError(TaskInformation taskInformation) {
    probe(taskInformation);
    throw new IllegalStateException("runtime error in tx");
  }

  private void probe(TaskInformation taskInformation) {
    invokedTasks.add(taskInformation.getTaskId());
    statusAtInvocation.add(synchronizationRegistry.getTransactionStatus());
    synchronizationRegistry.registerInterposedSynchronization(new Synchronization() {
      @Override
      public void beforeCompletion() {
      }

      @Override
      public void afterCompletion(int status) {
        completionStatus.add(status);
      }
    });
  }

  public List<Integer> getStatusAtInvocation() {
    return statusAtInvocation;
  }

  public List<Integer> getCompletionStatus() {
    return completionStatus;
  }

  public List<String> getInvokedTasks() {
    return invokedTasks;
  }

  public void reset() {
    statusAtInvocation.clear();
    completionStatus.clear();
    invokedTasks.clear();
  }
}
