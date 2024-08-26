package dev.bpmcrafters.example.order.fulfillment.order.adapter.out.usertasks;

import dev.bpmcrafters.example.order.fulfillment.order.application.port.out.UserTaskOutPort;
import dev.bpmcrafters.processengineapi.CommonRestrictions;
import dev.bpmcrafters.processengineapi.task.SubscribeForTaskCmd;
import dev.bpmcrafters.processengineapi.task.TaskInformation;
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi;
import dev.bpmcrafters.processengineapi.task.TaskType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * Simple user task pool.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserTaskPool implements UserTaskOutPort {

  private final TaskSubscriptionApi subscriptionApi;

  private final ConcurrentHashMap<TaskInformation, Map<String, ?>> tasks = new ConcurrentHashMap<>();

  @PostConstruct
  public void subscribe() throws ExecutionException, InterruptedException {
    subscriptionApi.subscribeForTask(
      new SubscribeForTaskCmd(
        CommonRestrictions
          .builder()
          .withProcessDefinitionKey("Process_OrderFulfillment_CCON2024")
          .build(),
        TaskType.USER,
        null,
        null,
        (taskInformation, payload) -> {
          log.info("Received user task for {} ({}:{})",
            taskInformation.getTaskId(),
            taskInformation.getMeta().get(CommonRestrictions.PROCESS_DEFINITION_KEY),
            taskInformation.getMeta().get(CommonRestrictions.TASK_DEFINITION_KEY));
          tasks.put(taskInformation, payload);
        },
        taskId -> {
          log.info("Removed user task {}", taskId);
          tasks.entrySet().removeIf(entry -> entry.getKey().getTaskId().equals(taskId));
        }
      )
    ).get();
  }

  @Override
  public TaskInformation getTask(String taskId) {
    return get(taskId).getKey();
  }

  @Override
  public Map<String, ?> getVariables(String taskId) {
    return get(taskId).getValue();
  }

  @Override
  public List<TaskInformation> getAllTasks() {
    return tasks.keySet().stream().toList();
  }

  private Map.Entry<TaskInformation, Map<String, ?>> get(String taskId) {
    return tasks.entrySet().stream().filter(taskEntry -> taskEntry.getKey().getTaskId().equals(taskId)).findFirst().get();
  }
}
