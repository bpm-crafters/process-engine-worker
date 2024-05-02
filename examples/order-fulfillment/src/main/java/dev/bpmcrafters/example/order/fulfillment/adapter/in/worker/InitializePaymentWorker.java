package dev.bpmcrafters.example.order.fulfillment.adapter.in.worker;

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengine.worker.Variable;
import dev.bpmcrafters.processengineapi.task.ExternalTaskCompletionApi;
import dev.bpmcrafters.processengineapi.task.TaskInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class InitializePaymentWorker {

  private static final Logger LOGGER = LoggerFactory.getLogger(InitializePaymentWorker.class);

  @ProcessEngineWorker()
  public void testMethod() {
  }

  @ProcessEngineWorker()
  public void testMethodWithPayload(Map<String, Object> payload) {

  }

  @ProcessEngineWorker()
  public void taskInformationAndPayload(TaskInformation taskInformation, Map<String, Object> payload) {

  }

  @ProcessEngineWorker()
  public void taskInformationAndCompletionPayload(TaskInformation taskInformation, ExternalTaskCompletionApi externalTaskCompletionApi, Map<String, Object> payload) {

  }

  @ProcessEngineWorker()
  public void taskInformation(TaskInformation taskInformation) {

  }

  @ProcessEngineWorker()
  public void taskInformationAndNamedVariables(TaskInformation taskInformation, @Variable(name = "foo") String foo, @Variable(name = "bar") Integer bar) {
  }

  @ProcessEngineWorker()
  public void namedVariables(@Variable(name = "foo") String foo, @Variable(name = "bar") Integer bar) {

  }

  @ProcessEngineWorker()
  public void completionApiAndNamedVariables(ExternalTaskCompletionApi externalTaskCompletionApi,  @Variable(name = "foo") String foo, @Variable(name = "bar") Integer bar) {
  }

  @ProcessEngineWorker()
  public Map<String, Object> taskInformationAndTaskCompletionAndNamedVariables(TaskInformation taskInformation, ExternalTaskCompletionApi externalTaskCompletionApi, @Variable(name = "foo") String foo, @Variable(name = "bar") Integer bar) {
    return null;
  }


}
