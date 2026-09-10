package dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.invalid;

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import jakarta.enterprise.inject.Vetoed;

@Vetoed
public class VetoedWorker {

  @ProcessEngineWorker(topic = "vetoed")
  public void work() {
  }
}
