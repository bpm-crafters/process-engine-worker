package dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.invalid;

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;

public abstract class AbstractMethodWorker {

  @ProcessEngineWorker(topic = "abstract")
  public abstract void work();

  public static class Impl extends AbstractMethodWorker {
    @Override
    public void work() {
    }
  }
}
