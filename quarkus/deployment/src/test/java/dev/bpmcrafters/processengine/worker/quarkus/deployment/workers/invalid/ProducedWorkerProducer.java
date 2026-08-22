package dev.bpmcrafters.processengine.worker.quarkus.deployment.workers.invalid;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@ApplicationScoped
public class ProducedWorkerProducer {

  @Produces
  @Singleton
  public ProducedWorker producedWorker() {
    return new ProducedWorker("https://example.org");
  }
}
