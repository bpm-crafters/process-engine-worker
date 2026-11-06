package dev.bpmcrafters.example.quarkus.adapter.out.memory;

import dev.bpmcrafters.example.quarkus.application.port.out.NotificationStore;
import dev.bpmcrafters.example.quarkus.domain.Notification;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Reconstructible leased view, not durable storage. */
@Singleton
public class InMemoryNotificationStore implements NotificationStore {
  private final ConcurrentHashMap<String, Notification> tasks = new ConcurrentHashMap<>();

  @Override
  public void putIfAbsent(Notification task) { tasks.putIfAbsent(task.taskId(), task); }
  @Override
  public Optional<Notification> find(String taskId) { return Optional.ofNullable(tasks.get(taskId)); }
  @Override
  public List<Notification> all() { return List.copyOf(tasks.values()); }
  @Override
  public void remove(String taskId) { tasks.remove(taskId); }
  @Override
  public void remove(String taskId, Notification expected) { tasks.remove(taskId, expected); }
}
