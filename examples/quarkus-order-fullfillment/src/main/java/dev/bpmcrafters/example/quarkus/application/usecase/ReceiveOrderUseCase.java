package dev.bpmcrafters.example.quarkus.application.usecase;

import dev.bpmcrafters.example.quarkus.application.port.in.ReceiveOrder;
import dev.bpmcrafters.example.quarkus.application.port.out.OrderPublisher;

public final class ReceiveOrderUseCase implements ReceiveOrder {
  private final OrderPublisher publisher;

  public ReceiveOrderUseCase(OrderPublisher publisher) { this.publisher = publisher; }

  @Override
  public void receive(Command command) throws Exception { publisher.publish(command.order()); }
}
