package dev.bpmcrafters.processengine.worker.quarkus.deployment.workers;

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengine.worker.Variable;
import dev.bpmcrafters.processengine.worker.registrar.VariableConverter;
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd;
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi;
import dev.bpmcrafters.processengineapi.task.TaskInformation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class InvocationWorker {

  public record Order(String id, int amount) {
  }

  private final List<Map<String, Object>> invocations = new CopyOnWriteArrayList<>();

  @ProcessEngineWorker(topic = "invoke")
  public Map<String, Object> invoke(
    @Variable("orderId") String orderId,
    @Variable(name = "order") Order order,
    @Variable(name = "missing", mandatory = false) String missing,
    TaskInformation taskInformation,
    Map<String, Object> payload,
    VariableConverter variableConverter,
    ServiceTaskCompletionApi completionApi
  ) {
    Map<String, Object> invocation = new HashMap<>();
    invocation.put("orderId", orderId);
    invocation.put("order", order);
    invocation.put("missing", missing);
    invocation.put("taskId", taskInformation.getTaskId());
    invocation.put("payload", payload);
    invocation.put("converter", variableConverter);
    invocation.put("completionApi", completionApi);
    invocations.add(invocation);
    return Map.of("result", "ok-" + orderId);
  }

  @ProcessEngineWorker(topic = "manual", autoComplete = false)
  public void manual(TaskInformation taskInformation, ServiceTaskCompletionApi completionApi) {
    invocations.add(Map.of("taskId", taskInformation.getTaskId()));
    completionApi.completeTask(new CompleteTaskCmd(taskInformation.getTaskId(), Map.of("manual", true)));
  }

  @ProcessEngineWorker(topic = "void-no-complete", autoComplete = false)
  public void noCompletion(TaskInformation taskInformation) {
    invocations.add(Map.of("taskId", taskInformation.getTaskId()));
  }

  public List<Map<String, Object>> getInvocations() {
    return invocations;
  }
}
