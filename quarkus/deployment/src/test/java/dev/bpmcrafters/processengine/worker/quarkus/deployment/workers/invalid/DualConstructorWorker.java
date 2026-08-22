package dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.invalid;

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.RecordingServiceTaskCompletionApi;

/**
 * Shape of a Kotlin class with default constructor arguments: a no-arg and a parameterised constructor, no @Inject.
 */
public class DualConstructorWorker {

  private final RecordingServiceTaskCompletionApi api;

  public DualConstructorWorker() {
    this(null);
  }

  public DualConstructorWorker(RecordingServiceTaskCompletionApi api) {
    this.api = api;
  }

  @ProcessEngineWorker(topic = "dual")
  public void work() {
    api.getCompleted();
  }
}
