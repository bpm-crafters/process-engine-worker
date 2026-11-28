package dev.bpmcrafters.example.order.fulfillment.order.domain;

public record Address(
  String streetLine,
  String zipCode,
  String city,
  String country
) {}
