package dev.bpmcrafters.example.order.fulfillment.payment.adapter.out.payment;

import dev.bpmcrafters.example.order.fulfillment.payment.application.port.out.PaymentOutPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Fake payment adapter failing to collect payment if the total is exactly 42.00.
 */
@Component
@Slf4j
public class FakePaymentAdapter implements PaymentOutPort {

  private static final BigDecimal MAGIC_NUMBER = BigDecimal.valueOf(42.00).setScale(2, RoundingMode.HALF_EVEN);

  @Override
  public String collectPayment(String reference, BigDecimal amount) {
    log.info("[PAYMENT] Collecting payment for {}", reference);
    if (amount.equals(MAGIC_NUMBER)) {
      log.info("[PAYMENT] Payment failed for {}.", reference);
      throw new IllegalArgumentException("Suspicious amount detected.");
    }

    log.info("[PAYMENT] Payment succeeded for {}.", reference);
    return UUID.randomUUID().toString();
  }
}
