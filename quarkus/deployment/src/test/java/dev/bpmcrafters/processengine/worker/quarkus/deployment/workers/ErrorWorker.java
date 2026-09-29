package dev.bpmcrafters.processengine.worker.quarkus.deployment.workers;

import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred;
import dev.bpmcrafters.processengine.worker.FailJobException;
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;

import java.time.Duration;
import java.util.Map;

public class ErrorWorker {

  @ProcessEngineWorker(topic = "bpmn-error")
  public void bpmnError() throws BpmnErrorOccurred {
    throw new BpmnErrorOccurred("business error happened", "BUSINESS_ERROR", Map.of("reason", "test"));
  }

  @ProcessEngineWorker(topic = "runtime-error")
  public void runtimeError() {
    throw new IllegalStateException("boom");
  }

  @ProcessEngineWorker(topic = "fail-job")
  public void failJob() throws FailJobException {
    throw new FailJobException("fail with retries", null, 7, Duration.ofSeconds(30));
  }
}
