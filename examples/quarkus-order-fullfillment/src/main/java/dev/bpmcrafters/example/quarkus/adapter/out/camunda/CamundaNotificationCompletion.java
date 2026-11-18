package dev.bpmcrafters.example.quarkus.adapter.out.camunda;

import dev.bpmcrafters.example.quarkus.application.port.out.NotificationCompletion;
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd;
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

@ApplicationScoped
public class CamundaNotificationCompletion implements NotificationCompletion {
  private final UserTaskCompletionApi completion;

  public CamundaNotificationCompletion(UserTaskCompletionApi completion) { this.completion = completion; }

  @Override
  public void complete(String taskId) throws Exception {
    completion.completeTask(new CompleteTaskCmd(taskId, Map.of("customer-informed", true))).get();
  }
}
