package dev.bpmcrafters.example.quarkus.application.port.in;

import dev.bpmcrafters.example.quarkus.domain.Order;
import dev.bpmcrafters.example.quarkus.domain.PaymentResult;

public interface FulfillOrder {
  PaymentResult retrievePayment(Command command);
  void fetchGoods(Command command) throws InterruptedException;
  void shipGoods(Command command);
  record Command(Order order) { }
}
