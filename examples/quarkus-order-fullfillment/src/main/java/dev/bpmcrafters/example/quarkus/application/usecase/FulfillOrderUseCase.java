package dev.bpmcrafters.example.quarkus.application.usecase;

import dev.bpmcrafters.example.quarkus.application.port.in.FulfillOrder;
import dev.bpmcrafters.example.quarkus.application.port.out.FulfillmentServices;
import dev.bpmcrafters.example.quarkus.domain.PaymentResult;

public final class FulfillOrderUseCase implements FulfillOrder {
  private final FulfillmentServices services;

  public FulfillOrderUseCase(FulfillmentServices services) { this.services = services; }

  @Override
  public PaymentResult retrievePayment(Command command) { return services.collectPayment(command.order()); }

  @Override
  public void fetchGoods(Command command) throws InterruptedException {
    if (command.order().requiresReplenishment()) services.replenish(command.order());
  }

  @Override
  public void shipGoods(Command command) { services.ship(command.order()); }
}
