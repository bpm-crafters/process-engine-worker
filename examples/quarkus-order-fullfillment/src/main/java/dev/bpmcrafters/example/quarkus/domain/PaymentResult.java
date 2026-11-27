package dev.bpmcrafters.example.quarkus.domain;

import java.math.BigDecimal;

public record PaymentResult(boolean received, String reference, String failureReason, BigDecimal amount) {
  public static final String REJECTION_REASON = "The amount 42.00 is rejected by the example payment service";

  public static PaymentResult forOrder(Order order) {
    final BigDecimal total = order.total();
    return total.compareTo(new BigDecimal("42.00")) == 0
      ? new PaymentResult(false, order.orderId().toString(), REJECTION_REASON, total)
      : new PaymentResult(true, order.orderId().toString(), null, total);
  }
}
