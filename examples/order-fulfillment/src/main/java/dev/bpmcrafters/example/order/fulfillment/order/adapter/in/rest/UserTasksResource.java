package dev.bpmcrafters.example.order.fulfillment.order.adapter.in.rest;

import dev.bpmcrafters.example.order.fulfillment.order.application.port.in.GetTasksForUserInPort;
import dev.bpmcrafters.processengineapi.task.TaskInformation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.http.ResponseEntity.ok;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/user-tasks", produces = MediaType.APPLICATION_JSON_VALUE)
public class UserTasksResource {

  private final GetTasksForUserInPort getTasksForUserInPort;

  @GetMapping()
  public ResponseEntity<List<TaskInformation>> getUserTasks() {
    return ok(getTasksForUserInPort.getTasks());
  }

}
