package dev.bpmcrafters.processengine.worker.quarkus.deployment.workers;

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;

/**
 * Overrides an annotated worker method of the abstract parent with a different topic; the override wins.
 */
public class OverridingTopicWorker extends OverridingTopicWorkerBase {

  @Override
  @ProcessEngineWorker(topic = "child-topic")
  public void run() {
  }
}
