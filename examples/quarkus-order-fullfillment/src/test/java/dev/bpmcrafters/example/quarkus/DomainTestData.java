package dev.bpmcrafters.example.quarkus;

import dev.bpmcrafters.example.quarkus.domain.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class DomainTestData {
  private DomainTestData() { }

  public static Address address() { return new Address("Main 1", "12345", "Berlin", "DE"); }
  public static Order order() { return order("10.00", "book"); }
  public static Order order(String price, String name) {
    return new Order(UUID.randomUUID(), List.of(new OrderPosition(name, 2, new BigDecimal(price))), address(), address());
  }
  public static Notification failed(String id) {
    final Order order = order("21.00", "book");
    return new Notification(id, Notification.Type.FAILED_PAYMENT, order, order.orderId().toString(), "Rejected", new BigDecimal("42.00"), null);
  }
  public static Notification delayed(String id) {
    return new Notification(id, Notification.Type.DELAYED_INVENTORY, order(), null, null, null, List.of("book"));
  }
}
