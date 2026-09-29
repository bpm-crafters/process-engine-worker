package dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.invalid;

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;

public class ConflictingTopicWorker {

  @ProcessEngineWorker(topic = "one", value = "two")
  public void work() {
  }
}
