package dev.bpmcrafters.example.quarkus.domain;

import java.math.BigDecimal;

public record OrderPosition(String name, Integer amount, BigDecimal price) {
  public OrderPosition {
    Validation.text(name);
    if (Validation.required(amount, "Amount") <= 0) throw new IllegalArgumentException("Positive amount required");
    if (Validation.required(price, "Price").signum() < 0) throw new IllegalArgumentException("Non-negative price required");
  }

  public BigDecimal subtotal() {
    return price.multiply(BigDecimal.valueOf(amount));
  }
}
