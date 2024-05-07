package dev.bpmcrafters.example.order.fulfillment.order.application.port.out;

import dev.bpmcrafters.processengineapi.task.TaskInformation;

import java.util.Map;

public interface UserTaskOutPort {

  TaskInformation getTask(String taskId);

  Map<String, ?> getVariables(String taskId);
}
