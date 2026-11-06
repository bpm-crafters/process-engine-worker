package dev.bpmcrafters.example.quarkus.adapter;

import dev.bpmcrafters.example.quarkus.domain.Notification;
import java.math.BigDecimal;
import java.util.List;

public final class NotificationMapper {
  public static final String PROCESS_ID = "Process_OrderFulfillment_CCON2024";
  public static final String FAILED_PAYMENT = "inform_customer_failed_payment";
  public static final String DELAYED_INVENTORY = "inform_customer_delay";

  private NotificationMapper() { }

  public static String type(Notification.Type type) {
    return switch (type) {
      case FAILED_PAYMENT -> FAILED_PAYMENT;
      case DELAYED_INVENTORY -> DELAYED_INVENTORY;
    };
  }

  public record Summary(String taskId, String type, String orderId) { }
  public record Details(String taskId, String type, OrderWire order, String paymentReference,
                        String failureReason, BigDecimal amount, List<String> items) { }

  public static Summary summary(Notification task) {
    return new Summary(task.taskId(), type(task.type()), task.order().orderId().toString());
  }

  public static Details details(Notification task) {
    return new Details(task.taskId(), type(task.type()), OrderMapper.toWire(task.order()), task.paymentReference(),
      task.failureReason(), task.amount(), task.items());
  }
}
