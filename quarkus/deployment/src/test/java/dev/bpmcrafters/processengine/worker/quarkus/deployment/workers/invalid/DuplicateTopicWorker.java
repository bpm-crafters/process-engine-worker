package dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.invalid;

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;

public class DuplicateTopicWorker {

  @ProcessEngineWorker(topic = "duplicate")
  public void first() {
  }

  @ProcessEngineWorker(value = "duplicate")
  public void second() {
  }
}
