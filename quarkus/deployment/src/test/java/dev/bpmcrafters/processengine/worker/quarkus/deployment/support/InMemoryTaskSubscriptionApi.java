package dev.bpmcrafters.processengine.worker.quarkus.deployment.support;

import dev.bpmcrafters.processengineapi.MetaInfo;
import dev.bpmcrafters.processengineapi.MetaInfoAware;
import dev.bpmcrafters.processengineapi.impl.task.AbstractTaskSubscriptionApiImpl;
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository;
import dev.bpmcrafters.processengineapi.impl.task.TaskSubscriptionHandle;
import dev.bpmcrafters.processengineapi.task.TaskInformation;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * In-memory task subscription API for tests, delivering tasks synchronously to the subscribed handlers.
 */
public class InMemoryTaskSubscriptionApi extends AbstractTaskSubscriptionApiImpl {

  private final InMemSubscriptionRepository repository;

  public InMemoryTaskSubscriptionApi() {
    this(new InMemSubscriptionRepository());
  }

  private InMemoryTaskSubscriptionApi(InMemSubscriptionRepository repository) {
    super(repository);
    this.repository = repository;
  }

  @Override
  public MetaInfo meta(MetaInfoAware instance) {
    return new MetaInfo() {
    };
  }

  public List<TaskSubscriptionHandle> getSubscriptions() {
    return repository.getTaskSubscriptions();
  }

  public TaskSubscriptionHandle getSubscription(String topic) {
    return getSubscriptions().stream()
      .filter(s -> Objects.equals(topic, s.getTaskDescriptionKey()))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("No subscription for topic " + topic + ", known: " + getSubscriptions()));
  }

  /**
   * Delivers a task to the subscription of the topic, invoking the worker synchronously on the calling thread.
   */
  public void deliver(String topic, TaskInformation taskInformation, Map<String, Object> payload) {
    getSubscription(topic).getAction().accept(taskInformation, payload);
  }

  public void deliver(String topic, String taskId, Map<String, Object> payload) {
    deliver(topic, taskInformation(taskId), payload);
  }

  public static TaskInformation taskInformation(String taskId) {
    return new TaskInformation(taskId, Map.of("processInstanceId", "instance-" + taskId, "retries", "3"));
  }
}
