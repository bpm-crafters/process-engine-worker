package dev.bpmcrafters.processengine.worker.quarkus.deployment.workers;

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengineapi.task.TaskInformation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Abstract base declaring worker methods; only the concrete subclass is a bean.
 */
public abstract class AbstractBaseWorker {

  protected final List<String> invocations = new CopyOnWriteArrayList<>();

  @ProcessEngineWorker(topic = "inherited")
  public Map<String, Object> inherited(TaskInformation taskInformation) {
    invocations.add("inherited:" + taskInformation.getTaskId());
    return Map.of("from", "base");
  }

  public abstract Map<String, Object> overridden(TaskInformation taskInformation);

  public List<String> getInvocations() {
    return invocations;
  }
}
