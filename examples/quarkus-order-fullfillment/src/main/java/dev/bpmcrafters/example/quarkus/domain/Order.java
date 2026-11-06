package dev.bpmcrafters.example.quarkus.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

public final class Order {
  private final UUID orderId;
  private final List<OrderPosition> orderPositions;
  private final Address shippingAddress;
  private final Address invoiceAddress;

  public Order(UUID orderId, List<OrderPosition> positions, Address shippingAddress, Address invoiceAddress) {
    this.orderId = Validation.required(orderId, "Order ID");
    Validation.required(positions, "Positions");
    if (positions.isEmpty()) throw new IllegalArgumentException("Positions required");
    positions.forEach(position -> Validation.required(position, "Position"));
    this.orderPositions = List.copyOf(positions);
    this.shippingAddress = Validation.required(shippingAddress, "Shipping address");
    this.invoiceAddress = Validation.required(invoiceAddress, "Invoice address");
  }

  public UUID orderId() { return orderId; }
  public List<OrderPosition> orderPositions() { return orderPositions; }
  public Address shippingAddress() { return shippingAddress; }
  public Address invoiceAddress() { return invoiceAddress; }

  public BigDecimal total() {
    return orderPositions.stream().map(OrderPosition::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add)
      .setScale(2, RoundingMode.HALF_EVEN);
  }

  public boolean requiresReplenishment() {
    return orderPositions.stream().anyMatch(position -> position.name().equals("out-of-stock"));
  }
}
