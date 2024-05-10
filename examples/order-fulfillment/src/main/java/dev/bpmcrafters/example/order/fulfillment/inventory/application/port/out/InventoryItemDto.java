package dev.bpmcrafters.example.order.fulfillment.inventory.application.port.out;

/**
 * Represents inventory item.
 * @param name name of the item.
 * @param amount amount to be fetched.
 */
public record InventoryItemDto(
  String name,
  Integer amount
) { }
