package dev.bpmcrafters.example.quarkus.application.port.out;

public interface NotificationCompletion {
  void complete(String taskId) throws Exception;
}
