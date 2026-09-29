package dev.bpmcrafters.processengine.worker.quarkus.deployment.workers;

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;

public class CustomBeansWorker {

  @ProcessEngineWorker(topic = "custom")
  public String work(CustomBeans.Greeting greeting) {
    return greeting.text();
  }
}
