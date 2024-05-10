package dev.bpmcrafters.example.order.fulfillment.payment.application.port.in;

import lombok.Getter;

import java.util.Map;

/**
 * Exception indicating payment errors.
 */
@Getter
public class PaymentFailedException extends RuntimeException {

  private final String errorCode;
  private final Map<String, Object> details;

  public PaymentFailedException(String message, String reason) {
    super(message);
    this.errorCode = "paymentFailed";
    this.details = Map.of(
        "paymentReceived", false,
        "paymentFailedReason", reason
      );
  }
}
