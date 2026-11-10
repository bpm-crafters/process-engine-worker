package dev.bpmcrafters.example.quarkus.adapter;

import dev.bpmcrafters.example.quarkus.domain.Address;
import dev.bpmcrafters.example.quarkus.domain.Order;
import dev.bpmcrafters.example.quarkus.domain.OrderPosition;
import dev.bpmcrafters.example.quarkus.domain.Validation;

public final class OrderMapper {
  private OrderMapper() { }

  public static Order toDomain(OrderWire wire) {
    Validation.required(wire, "Order");
    return new Order(wire.orderId(), Validation.required(wire.orderPositions(), "Positions").stream()
      .map(position -> {
        Validation.required(position, "Position");
        return new OrderPosition(position.name(), position.amount(), position.price());
      }).toList(), address(wire.shippingAddress()), address(wire.invoiceAddress()));
  }

  private static Address address(OrderWire.Address address) {
    Validation.required(address, "Address");
    return new Address(address.streetLine(), address.zipCode(), address.city(), address.country());
  }

  public static OrderWire toWire(Order order) {
    return new OrderWire(order.orderId(), order.orderPositions().stream()
      .map(position -> new OrderWire.Position(position.name(), position.amount(), position.price())).toList(),
      address(order.shippingAddress()), address(order.invoiceAddress()));
  }

  private static OrderWire.Address address(Address address) {
    return new OrderWire.Address(address.streetLine(), address.zipCode(), address.city(), address.country());
  }
}
