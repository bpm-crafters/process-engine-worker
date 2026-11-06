package dev.bpmcrafters.example.quarkus.application.port.out;

import dev.bpmcrafters.example.quarkus.domain.Order;
import dev.bpmcrafters.example.quarkus.domain.PaymentResult;

public interface FulfillmentServices {
  PaymentResult collectPayment(Order order);
  void replenish(Order order) throws InterruptedException;
  void ship(Order order);
}
