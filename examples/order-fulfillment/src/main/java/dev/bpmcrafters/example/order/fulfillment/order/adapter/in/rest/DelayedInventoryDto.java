package dev.bpmcrafters.example.order.fulfillment.order.adapter.in.rest;

import java.util.List;

/**
 * DTO to inform about delayed inventory.
 */
public record DelayedInventoryDto(
  String orderId,
  List<String> items
) {
}
