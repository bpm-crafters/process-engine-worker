package dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.invalid;

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;

public class PackagePrivateMethodWorker {

  @ProcessEngineWorker(topic = "package-private")
  void work() {
  }
}
