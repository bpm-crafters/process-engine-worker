package dev.bpmcrafters.example.order.fulfillment.payment.application.port.out;

import java.math.BigDecimal;

/**
 * Outbound port to receive payments.
 */
public interface PaymentOutPort {
  /**
   * Collects a payment for given reference and amount.
   * @param reference payment reference.
   * @param amount amount to collect.
   * @return payment id.
   * @throws IllegalArgumentException if the amount is suspicious.
   */
  String collectPayment(String reference, BigDecimal amount);
}
