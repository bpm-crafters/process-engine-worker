package dev.bpmcrafters.example.quarkus.application.port.in;

import dev.bpmcrafters.example.quarkus.domain.Order;

public interface ReceiveOrder {
  void receive(Command command) throws Exception;
  record Command(Order order) { }
}
