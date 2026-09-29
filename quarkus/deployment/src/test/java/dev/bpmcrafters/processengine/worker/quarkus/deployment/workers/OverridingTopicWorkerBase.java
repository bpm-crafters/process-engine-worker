package dev.bpmcrafters.processengine.worker.quarkus.deployment.workers;

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;

public abstract class OverridingTopicWorkerBase {

  @ProcessEngineWorker(topic = "parent-topic")
  public void run() {
  }
}
