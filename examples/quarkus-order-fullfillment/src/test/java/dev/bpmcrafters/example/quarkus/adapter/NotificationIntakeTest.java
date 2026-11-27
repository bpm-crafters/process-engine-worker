package dev.bpmcrafters.example.quarkus.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bpmcrafters.example.quarkus.adapter.in.worker.NotificationIntake;
import dev.bpmcrafters.example.quarkus.adapter.out.memory.InMemoryNotificationStore;
import dev.bpmcrafters.example.quarkus.application.usecase.NotificationUseCase;
import dev.bpmcrafters.example.quarkus.domain.Notification;
import dev.bpmcrafters.processengineapi.CommonRestrictions;
import dev.bpmcrafters.processengineapi.task.*;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static dev.bpmcrafters.example.quarkus.DomainTestData.*;
import static dev.bpmcrafters.example.quarkus.adapter.NotificationMapper.*;
import static org.assertj.core.api.Assertions.*;

class NotificationIntakeTest {
  private final InMemoryNotificationStore store = new InMemoryNotificationStore();
  private final NotificationUseCase notifications = new NotificationUseCase(store, id -> { });
  private final NotificationIntake intake = new NotificationIntake(new ObjectMapper(), notifications);

  private TaskInformation task(String id, String type) {
    return new TaskInformation(id, Map.of(CommonRestrictions.PROCESS_DEFINITION_KEY, PROCESS_ID, CommonRestrictions.ACTIVITY_ID, type));
  }

  private Map<String, Object> payload() {
    return Map.of("order", OrderMapper.toWire(order("21.00", "book")), "paymentFailedReason", "Rejected", "paymentAmount", "42.00");
  }

  @Test
  void subscriptionRequestsAllVariablesAndValidatesMetadataAndPayload() {
    final var command = intake.command();
    assertThat(command.getRestrictions()).containsExactlyInAnyOrderEntriesOf(Map.of(CommonRestrictions.PROCESS_DEFINITION_KEY, PROCESS_ID));
    assertThat(command.getTaskType()).isEqualTo(TaskType.USER);
    assertThat(command.getTaskDescriptionKey()).isNull();
    assertThat(command.getPayloadDescription()).isNull();
    intake.accept(task("null", FAILED_PAYMENT), null);
    intake.accept(new TaskInformation("other", Map.of()), payload());
    intake.accept(new TaskInformation("other", Map.of(CommonRestrictions.PROCESS_DEFINITION_KEY, "other")), payload());
    intake.accept(task("wrong", "wrong"), payload());
    intake.accept(task("missingType", PROCESS_ID), payload());
    for (final Map<String, ?> malformed : List.<Map<String, ?>>of(Map.of(), Map.of("order", Map.of()),
        Map.of("order", "not an order"), Map.of("order", OrderMapper.toWire(order())),
        Map.of("order", OrderMapper.toWire(order()), "paymentFailedReason", 1, "paymentAmount", "42"),
        Map.of("order", OrderMapper.toWire(order()), "paymentFailedReason", "reason", "paymentAmount", "NaN"))) {
      intake.accept(task("bad", FAILED_PAYMENT), malformed);
    }
    assertThat(notifications.list()).isEmpty();
    command.getAction().accept(task("1", FAILED_PAYMENT), payload());
    final Notification accepted = store.find("1").orElseThrow();
    assertThat(accepted.paymentReference()).isEqualTo(accepted.order().orderId().toString());
    assertThat(accepted.amount()).isEqualByComparingTo("42.00");
  }

  @Test
  void nullRecordFieldsAndInvalidDomainValuesCannotPartiallyEnterStore() {
    final ObjectMapper mapper = new ObjectMapper();
    final Map<String, Object> wire = mapper.convertValue(OrderMapper.toWire(order()), new com.fasterxml.jackson.core.type.TypeReference<>() { });
    for (final String key : List.of("orderId", "shippingAddress", "invoiceAddress", "orderPositions")) {
      final var invalid = new HashMap<>(wire);
      invalid.put(key, null);
      intake.accept(task("bad", DELAYED_INVENTORY), Map.of("order", invalid));
    }
    final var invalid = new HashMap<>(wire);
    invalid.put("orderPositions", List.of(Map.of("name", "book", "price", "1")));
    intake.accept(task("bad", DELAYED_INVENTORY), Map.of("order", invalid));
    assertThat(store.all()).isEmpty();
  }

  @Test
  void duplicateDoesNotOverwriteAndTerminationIsIdempotent() {
    final var task = task("1", FAILED_PAYMENT);
    intake.accept(task, payload());
    final var first = store.find("1").orElseThrow();
    final var changed = new HashMap<>(payload());
    changed.put("paymentFailedReason", "changed");
    intake.accept(task.withReason("update"), changed);
    assertThat(store.find("1")).containsSame(first);
    store.remove("1", failed("1"));
    assertThat(store.find("1")).containsSame(first);
    intake.command().getTermination().accept(task);
    intake.command().getTermination().accept(task);
    assertThat(store.all()).isEmpty();
  }

  @Test
  void delayedItemsIncludeAllPositionsAndQueriesAreLexical() {
    final var order = order();
    final var mixed = new dev.bpmcrafters.example.quarkus.domain.Order(order.orderId(), List.of(
      new dev.bpmcrafters.example.quarkus.domain.OrderPosition("out-of-stock", 1, java.math.BigDecimal.ONE),
      new dev.bpmcrafters.example.quarkus.domain.OrderPosition("book", 1, java.math.BigDecimal.ZERO)), order.shippingAddress(), order.invoiceAddress());
    for (final String id : List.of("2", "10", "1")) intake.accept(task(id, DELAYED_INVENTORY), Map.of("order", OrderMapper.toWire(mixed)));
    assertThat(notifications.list()).extracting(Notification::taskId).containsExactly("1", "10", "2");
    assertThat(store.find("1").orElseThrow().items()).containsExactly("out-of-stock", "book");
  }
}
