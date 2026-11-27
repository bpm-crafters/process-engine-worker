package dev.bpmcrafters.example.quarkus.application.port.out;

import dev.bpmcrafters.example.quarkus.domain.Notification;
import java.util.List;
import java.util.Optional;

public interface NotificationStore {
  void putIfAbsent(Notification notification);
  Optional<Notification> find(String taskId);
  List<Notification> all();
  void remove(String taskId);
  void remove(String taskId, Notification expected);
}
