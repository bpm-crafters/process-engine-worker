package dev.bpmcrafters.example.order.fulfillment.order.adapter.in.rest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderDto(
  UUID id,
  AddressDto shipmentAddress,
  AddressDto invoiceAddress,
  List<OrderPositionDto> orderPositions
) { }

record AddressDto(
  String streetLine,
  String zipCode,
  String city,
  String country
) { }

record OrderPositionDto(
  String name,
  Integer amount,
  BigDecimal price
) { }
