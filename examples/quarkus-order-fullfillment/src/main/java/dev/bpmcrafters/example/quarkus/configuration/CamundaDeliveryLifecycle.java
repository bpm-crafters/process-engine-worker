package dev.bpmcrafters.example.quarkus.configuration;

import dev.bpmcrafters.example.quarkus.adapter.in.worker.NotificationIntake;
import dev.bpmcrafters.processengineapi.adapter.c8.task.delivery.SubscribingRefreshingZeebeJobUserTaskDelivery;
import dev.bpmcrafters.processengineapi.adapter.c8.task.delivery.SubscribingServiceTaskDelivery;
import dev.bpmcrafters.processengineapi.task.*;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class CamundaDeliveryLifecycle {
  private final boolean enabled;
  private final NotificationIntake intake;
  private final Instance<TaskSubscriptionApi> subscriptions;
  private final Instance<SubscribingServiceTaskDelivery> services;
  private final Instance<SubscribingRefreshingZeebeJobUserTaskDelivery> users;
  private ScheduledExecutorService refresh;
  private SubscribingRefreshingZeebeJobUserTaskDelivery startedUsers;
  private TaskSubscription subscription;

  public CamundaDeliveryLifecycle(@ConfigProperty(name = "example.camunda.startup-enabled", defaultValue = "true") boolean enabled,
      NotificationIntake intake, Instance<TaskSubscriptionApi> subscriptions, Instance<SubscribingServiceTaskDelivery> services,
      Instance<SubscribingRefreshingZeebeJobUserTaskDelivery> users) {
    this.enabled = enabled;
    this.intake = intake;
    this.subscriptions = subscriptions;
    this.services = services;
    this.users = users;
  }

  public void register(@Observes @Priority(2400) StartupEvent event) throws Exception {
    if (!enabled) return;
    subscription = subscriptions.get().subscribeForTask(intake.command()).get();
  }

  public void activate(@Observes @Priority(3000) StartupEvent event) {
    if (!enabled) return;
    services.get().subscribe(); // Snapshot only after extension registration at priority 2500.
    final var delivery = users.get();
    startedUsers = delivery;
    delivery.subscribe();
    refresh = Executors.newSingleThreadScheduledExecutor(task -> {
      final Thread thread = new Thread(task, "order-user-task-refresh");
      thread.setDaemon(true);
      return thread;
    });
    refresh.scheduleWithFixedDelay(() -> refreshSafely(delivery::refresh), 5, 5, TimeUnit.SECONDS);
  }

  public void stop(@Observes @Priority(2000) ShutdownEvent event) throws Exception {
    try {
      stopRefresh();
    } finally {
      try {
        if (startedUsers != null) startedUsers.unsubscribeAll();
      } finally {
        if (subscription != null) {
          final TaskSubscription registered = subscription;
          subscription = null;
          subscriptions.get().unsubscribe(new UnsubscribeFromTaskCmd(registered)).get();
        }
      }
    }
  }

  private void stopRefresh() throws InterruptedException {
    if (refresh == null) return;
    refresh.shutdownNow();
    try {
      refresh.awaitTermination(10, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw exception;
    }
  }

  public static void refreshSafely(Runnable action) {
    try {
      action.run();
    } catch (Exception exception) {
      Logger.getLogger(CamundaDeliveryLifecycle.class).warn("User task refresh failed; retrying next cycle", exception);
    }
  }
}
