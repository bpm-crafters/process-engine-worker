package dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.invalid;

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;

public class StaticMethodWorker {

  @ProcessEngineWorker(topic = "static")
  public static void work() {
  }
}
