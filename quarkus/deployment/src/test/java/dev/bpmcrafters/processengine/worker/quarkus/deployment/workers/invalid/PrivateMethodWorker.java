package dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.invalid;

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;

public class PrivateMethodWorker {

  @ProcessEngineWorker(topic = "private")
  private void work() {
  }
}
