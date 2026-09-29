package dev.bpmcrafters.processengine.worker.quarkus.deployment.workers;

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengine.worker.Variable;

import java.util.Map;

/**
 * Worker class without any scope annotation: the extension turns it into a bean.
 */
public class SimpleWorker {

  @ProcessEngineWorker(topic = "topic-a", lockDuration = 5000, tenantId = "tenant-a")
  public void workerA(@Variable("orderId") String orderId, @Variable(name = "amount", mandatory = false) Integer amount) {
  }

  @ProcessEngineWorker(value = "topic-b")
  public Map<String, Object> workerB(Map<String, Object> payload) {
    return Map.of("done", true);
  }

  @ProcessEngineWorker
  public void workerC() {
  }

  /**
   * Java shorthand binds to the {@code value} attribute.
   */
  @ProcessEngineWorker("topic-d")
  public void workerD() {
  }
}
