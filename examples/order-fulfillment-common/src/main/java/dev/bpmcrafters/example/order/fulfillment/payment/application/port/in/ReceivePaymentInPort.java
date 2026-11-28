package dev.bpmcrafters.example.order.fulfillment.payment.application.port.in;

import dev.bpmcrafters.example.order.fulfillment.order.domain.Order;

/**
 * Use case in port to receive payment.
 */
public interface ReceivePaymentInPort {

  /**
   * Receives payment.
   * @param order order to receive payment for.
   * @return payment id.
   * @throws PaymentFailedException on any errors.
   */
  String receivePayment(Order order) throws PaymentFailedException;
}
