package dev.bpmcrafters.example.order.fulfillment.order.application.port.in;

import dev.bpmcrafters.processengineapi.task.TaskInformation;

import java.util.List;

/**
 * Application inbound port to retrieve user tasks for the user.
 */
public interface GetTasksForUserInPort {
  /**
   * Retrieves all user tasks.
   * @return user tasks.
   */
  List<TaskInformation> getTasks();
}
