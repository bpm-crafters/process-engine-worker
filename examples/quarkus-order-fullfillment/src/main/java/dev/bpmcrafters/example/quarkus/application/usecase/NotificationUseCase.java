package dev.bpmcrafters.example.quarkus.application.usecase;

import dev.bpmcrafters.example.quarkus.application.port.in.ManageNotifications;
import dev.bpmcrafters.example.quarkus.application.port.out.NotificationCompletion;
import dev.bpmcrafters.example.quarkus.application.port.out.NotificationStore;
import dev.bpmcrafters.example.quarkus.domain.Notification;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class NotificationUseCase implements ManageNotifications {
  private final NotificationStore store;
  private final NotificationCompletion completion;

  public NotificationUseCase(NotificationStore store, NotificationCompletion completion) {
    this.store = store;
    this.completion = completion;
  }

  @Override
  public void accept(Notification notification) { store.putIfAbsent(notification); }

  @Override
  public void terminate(String taskId) { store.remove(taskId); }

  @Override
  public List<Notification> list() {
    return store.all().stream().sorted(Comparator.comparing(Notification::taskId)).toList();
  }

  @Override
  public Optional<Notification> find(Command command) {
    return store.find(command.taskId()).filter(task -> task.type() == command.type());
  }

  @Override
  public boolean complete(Command command) throws Exception {
    final Optional<Notification> task = find(command);
    if (task.isEmpty()) return false;
    completion.complete(command.taskId());
    store.remove(command.taskId(), task.get());
    return true;
  }
}
