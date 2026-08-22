package dev.bpmcrafters.processengine.worker.quarkus.deployment.support;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * Exposes the in-memory engine API fakes as beans.
 */
@ApplicationScoped
public class TestApiProducer {

  @Produces
  @Singleton
  public InMemoryTaskSubscriptionApi taskSubscriptionApi() {
    return new InMemoryTaskSubscriptionApi();
  }

  @Produces
  @Singleton
  public RecordingServiceTaskCompletionApi taskCompletionApi() {
    return new RecordingServiceTaskCompletionApi();
  }
}
