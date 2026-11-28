package dev.bpmcrafters.example.order.fulfillment.order.domain;


import java.util.List;
import java.util.UUID;

public record Order(
  UUID orderId,
  List<OrderPosition> orderPositions,
  Address shippingAddress,
  Address invoiceAddress
) {
}


