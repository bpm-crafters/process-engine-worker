package dev.bpmcrafters.example.order.fulfillment.order.application.port.in;

import dev.bpmcrafters.example.order.fulfillment.order.domain.Order;

/**
 * Application inbound port to trigger order receipt via ingress.
 */
public interface ReceiveOrderInPort {
  /**
   * Receives order.
   * @param order order to receive.
   */
  void orderReceived(Order order);
}
