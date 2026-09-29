package dev.bpmcrafters.processengine.worker.quarkus.deployment.workers;

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengineapi.task.TaskInformation;

import java.util.Map;

public class ConcreteWorker extends AbstractBaseWorker {

  @Override
  @ProcessEngineWorker(topic = "overridden")
  public Map<String, Object> overridden(TaskInformation taskInformation) {
    invocations.add("overridden:" + taskInformation.getTaskId());
    return Map.of("from", "concrete");
  }
}
