package dev.bpmcrafters.example.quarkus.domain;

import java.math.BigDecimal;
import java.util.List;

public record Notification(String taskId, Type type, Order order, String paymentReference,
                           String failureReason, BigDecimal amount, List<String> items) {
  public enum Type { FAILED_PAYMENT, DELAYED_INVENTORY }

  public Notification {
    Validation.text(taskId);
    Validation.required(type, "Type");
    Validation.required(order, "Order");
    if (type == Type.FAILED_PAYMENT) {
      Validation.text(paymentReference);
      Validation.text(failureReason);
      Validation.required(amount, "Amount");
    }
    if (type == Type.DELAYED_INVENTORY) items = List.copyOf(Validation.required(items, "Items"));
  }
}
