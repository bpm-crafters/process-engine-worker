package dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.invalid;

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;

public class ProducedWorker {

  private final String url;

  public ProducedWorker(String url) {
    this.url = url;
  }

  @ProcessEngineWorker(topic = "produced")
  public void work() {
  }

  public String getUrl() {
    return url;
  }
}
