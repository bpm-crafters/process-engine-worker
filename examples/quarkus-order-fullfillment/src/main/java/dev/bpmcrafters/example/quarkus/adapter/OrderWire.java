package dev.bpmcrafters.example.quarkus.adapter;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Explicit process/HTTP schema, independent of aggregate accessor conventions. */
public record OrderWire(UUID orderId, List<Position> orderPositions, Address shippingAddress, Address invoiceAddress) {
  public record Address(String streetLine, String zipCode, String city, String country) { }
  public record Position(String name, @JsonDeserialize(using = IntegerQuantityDeserializer.class) Integer amount, BigDecimal price) { }
}
