package dev.bpmcrafters.example.quarkus.configuration;

import dev.bpmcrafters.example.quarkus.application.port.in.*;
import dev.bpmcrafters.example.quarkus.application.port.out.*;
import dev.bpmcrafters.example.quarkus.application.usecase.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@ApplicationScoped
public class ApplicationComposition {
  @Produces
  @Singleton
  public ReceiveOrder orders(OrderPublisher publisher) { return new ReceiveOrderUseCase(publisher); }

  @Produces
  @Singleton
  public FulfillOrder fulfillment(FulfillmentServices services) { return new FulfillOrderUseCase(services); }

  @Produces
  @Singleton
  public ManageNotifications notifications(NotificationStore store, NotificationCompletion completion) {
    return new NotificationUseCase(store, completion);
  }

  @Produces
  @Singleton
  public DeployProcess deployment(ProcessDeployment deployment) { return new DeployProcessUseCase(deployment); }
}
