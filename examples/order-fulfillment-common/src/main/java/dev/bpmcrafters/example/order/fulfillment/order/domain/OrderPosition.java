package dev.bpmcrafters.example.order.fulfillment.order.domain;

import java.math.BigDecimal;

public record OrderPosition(
  String name,
  Integer amount,
  BigDecimal price
) { }
