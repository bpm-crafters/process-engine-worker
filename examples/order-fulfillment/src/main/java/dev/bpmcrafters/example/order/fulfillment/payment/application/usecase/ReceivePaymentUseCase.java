package dev.bpmcrafters.example.order.fulfillment.payment.application.usecase;

import dev.bpmcrafters.example.order.fulfillment.order.domain.Order;
import dev.bpmcrafters.example.order.fulfillment.payment.application.port.in.PaymentFailedException;
import dev.bpmcrafters.example.order.fulfillment.payment.application.port.in.ReceivePaymentInPort;
import dev.bpmcrafters.example.order.fulfillment.payment.application.port.out.PaymentOutPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Use case for payment execution.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReceivePaymentUseCase implements ReceivePaymentInPort {

  private final PaymentOutPort paymentOutPort;

  @Override
  public String receivePayment(Order order) throws PaymentFailedException {
    var total = calculateTotal(order);
    try {
      return paymentOutPort.collectPayment(order.orderId().toString(), total);
    } catch (Exception e) {
      throw new PaymentFailedException("Payment ", e.getMessage(), total);
    }
  }

  /*
   * Calculates order total.
   */
  private BigDecimal calculateTotal(Order order) {
    return BigDecimal
      .valueOf(order.orderPositions().stream().mapToDouble(p -> p.price().doubleValue() * p.amount()).sum())
      .setScale(2, RoundingMode.HALF_EVEN);
  }
}
