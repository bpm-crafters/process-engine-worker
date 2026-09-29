package dev.bpmcrafters.processengine.worker.quarkus.deployment.workers;

import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred;
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengineapi.task.TaskInformation;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Workers used for the idempotency tests. The 'fail-first' worker fails on its first invocation for a task.
 */
public class IdempotentWorker {

  private final List<String> invocations = new CopyOnWriteArrayList<>();

  @Transactional
  @ProcessEngineWorker(topic = "idempotent-tx")
  public Map<String, Object> transactional(TaskInformation taskInformation) {
    invocations.add(taskInformation.getTaskId());
    return Map.of("result", "tx-" + taskInformation.getTaskId());
  }

  @ProcessEngineWorker(topic = "idempotent-plain")
  public Map<String, Object> plain(TaskInformation taskInformation) {
    invocations.add(taskInformation.getTaskId());
    return Map.of("result", "plain-" + taskInformation.getTaskId());
  }

  @Transactional
  @ProcessEngineWorker(topic = "idempotent-fail-first")
  public Map<String, Object> failFirst(TaskInformation taskInformation) {
    invocations.add(taskInformation.getTaskId());
    if (invocations.stream().filter(taskInformation.getTaskId()::equals).count() == 1) {
      throw new IllegalStateException("first invocation fails");
    }
    return Map.of("result", "second");
  }

  @Transactional
  @ProcessEngineWorker(topic = "idempotent-bpmn-error")
  public void bpmnError(TaskInformation taskInformation) throws BpmnErrorOccurred {
    invocations.add(taskInformation.getTaskId());
    throw new BpmnErrorOccurred("error", "IDEMPOTENT_ERROR", Map.of());
  }

  public List<String> getInvocations() {
    return invocations;
  }

  public long invocationsOf(String taskId) {
    return invocations.stream().filter(taskId::equals).count();
  }
}
