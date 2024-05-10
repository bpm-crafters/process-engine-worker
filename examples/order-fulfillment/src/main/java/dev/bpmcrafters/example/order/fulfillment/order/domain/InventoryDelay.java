package dev.bpmcrafters.example.order.fulfillment.order.domain;

import java.util.List;

/**
 * Delay of the inventory items.
 * @param orderId order id.
 * @param items list of item names.
 */
public record InventoryDelay(
  String orderId,
  List<String> items
) {}
