package dev.bpmcrafters.example.quarkus.application.port.in;

import dev.bpmcrafters.example.quarkus.domain.Notification;
import java.util.List;
import java.util.Optional;

public interface ManageNotifications {
  void accept(Notification notification);
  void terminate(String taskId);
  List<Notification> list();
  Optional<Notification> find(Command command);
  boolean complete(Command command) throws Exception;
  record Command(String taskId, Notification.Type type) { }
}
