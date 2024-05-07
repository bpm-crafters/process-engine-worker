package dev.bpmcrafters.example.order.fulfillment.payment.application.usecase;

import dev.bpmcrafters.example.order.fulfillment.payment.application.port.in.PaymentFailedException;
import dev.bpmcrafters.example.order.fulfillment.payment.application.port.in.ReceivePaymentInPort;
import dev.bpmcrafters.example.order.fulfillment.order.domain.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Component
@Slf4j
public class ReceivePaymentUseCase implements ReceivePaymentInPort {

  private static final BigDecimal MAGIC_NUMBER = BigDecimal.valueOf(42.00).setScale(2, RoundingMode.HALF_EVEN);

  @Override
  public String receivePayment(Order order) throws PaymentFailedException {

    log.info("[PAYMENT]: Receiving payment for {}, invoicing {}", order.orderId(), order.invoiceAddress());

    var total = calculateTotal(order);
    if (total.equals(MAGIC_NUMBER)) {
      log.info("[PAYMENT]: Payment failed.");
      throw new PaymentFailedException();
    }
    log.info("[PAYMENT]: Payment succeeded.");
    return UUID.randomUUID().toString();
  }

  private BigDecimal calculateTotal(Order order) {
    return BigDecimal
      .valueOf(order.orderPositions().stream().mapToDouble(p -> p.price().doubleValue() * p.amount()).sum())
      .setScale(2, RoundingMode.HALF_EVEN);
  }
}
