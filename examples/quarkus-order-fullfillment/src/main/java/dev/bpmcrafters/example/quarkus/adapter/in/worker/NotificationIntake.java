package dev.bpmcrafters.example.quarkus.adapter.in.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bpmcrafters.example.quarkus.adapter.OrderMapper;
import dev.bpmcrafters.example.quarkus.adapter.OrderWire;
import dev.bpmcrafters.example.quarkus.application.port.in.ManageNotifications;
import dev.bpmcrafters.example.quarkus.domain.Notification;
import dev.bpmcrafters.example.quarkus.domain.Order;
import dev.bpmcrafters.example.quarkus.domain.OrderPosition;
import dev.bpmcrafters.processengineapi.CommonRestrictions;
import dev.bpmcrafters.processengineapi.task.SubscribeForTaskCmd;
import dev.bpmcrafters.processengineapi.task.TaskInformation;
import dev.bpmcrafters.processengineapi.task.TaskTerminationHandler;
import dev.bpmcrafters.processengineapi.task.TaskType;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.util.Map;
import static dev.bpmcrafters.example.quarkus.adapter.NotificationMapper.*;

@ApplicationScoped
public class NotificationIntake {
  private final ObjectMapper mapper;
  private final ManageNotifications notifications;

  public NotificationIntake(ObjectMapper mapper, ManageNotifications notifications) {
    this.mapper = mapper;
    this.notifications = notifications;
  }

  public SubscribeForTaskCmd command() {
    return new SubscribeForTaskCmd(Map.of(CommonRestrictions.PROCESS_DEFINITION_KEY, PROCESS_ID), TaskType.USER, null, null,
      this::accept, (TaskTerminationHandler) task -> notifications.terminate(task.getTaskId()));
  }

  public void accept(TaskInformation task, Map<String, ?> payload) {
    if (payload == null) return;
    if (!PROCESS_ID.equals(task.getMeta().get(CommonRestrictions.PROCESS_DEFINITION_KEY))) return;
    final String type = task.getMeta().get(CommonRestrictions.ACTIVITY_ID);
    if (!FAILED_PAYMENT.equals(type) && !DELAYED_INVENTORY.equals(type)) return;
    final Notification notification;
    try {
      notification = map(task.getTaskId(), type, payload);
    } catch (IllegalArgumentException exception) {
      return; // Malformed deliveries must never partially enter the leased view.
    }
    notifications.accept(notification);
  }

  private Notification map(String taskId, String type, Map<String, ?> payload) {
    final Order order = OrderMapper.toDomain(mapper.convertValue(payload.get("order"), OrderWire.class));
    if (FAILED_PAYMENT.equals(type)) {
      final Object reason = payload.get("paymentFailedReason");
      final Object amount = payload.get("paymentAmount");
      if (!(reason instanceof String) || amount == null) throw new IllegalArgumentException("Payment details required");
      return new Notification(taskId, Notification.Type.FAILED_PAYMENT, order, order.orderId().toString(),
        (String) reason, new BigDecimal(amount.toString()), null);
    }
    return new Notification(taskId, Notification.Type.DELAYED_INVENTORY, order, null, null, null,
      order.orderPositions().stream().map(OrderPosition::name).toList());
  }
}
