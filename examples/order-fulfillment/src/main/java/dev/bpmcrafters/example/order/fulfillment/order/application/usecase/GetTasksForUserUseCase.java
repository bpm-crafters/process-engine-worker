package dev.bpmcrafters.example.order.fulfillment.order.application.usecase;

import dev.bpmcrafters.example.order.fulfillment.order.application.port.in.GetTasksForUserInPort;
import dev.bpmcrafters.example.order.fulfillment.order.application.port.out.UserTaskOutPort;
import dev.bpmcrafters.processengineapi.task.TaskInformation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Use case for user task retrieval.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GetTasksForUserUseCase implements GetTasksForUserInPort {

  private final UserTaskOutPort userTaskOutPort;

  @Override
  public List<TaskInformation> getTasks() {
    return userTaskOutPort.getAllTasks();
  }
}
