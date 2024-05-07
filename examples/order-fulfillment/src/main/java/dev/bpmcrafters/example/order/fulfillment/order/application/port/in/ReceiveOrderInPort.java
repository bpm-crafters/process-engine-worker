package dev.bpmcrafters.example.order.fulfillment.order.application.port.in;

import dev.bpmcrafters.example.order.fulfillment.order.domain.Order;

public interface ReceiveOrderInPort {
  void orderReceived(Order order);
}
