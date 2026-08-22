package dev.bpmcrafters.processengine.worker.quarkus.deployment.workers;

import dev.bpmcrafters.processengine.worker.quarkus.deployment.support.InMemoryTaskSubscriptionApi;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Records the number of subscriptions present when the startup event is fired.
 */
@ApplicationScoped
public class StartupObserver {

  private volatile int subscriptionsAtStartup = -1;

  void onStart(@Observes StartupEvent event, InMemoryTaskSubscriptionApi api) {
    subscriptionsAtStartup = api.getSubscriptions().size();
  }

  public int getSubscriptionsAtStartup() {
    return subscriptionsAtStartup;
  }
}
