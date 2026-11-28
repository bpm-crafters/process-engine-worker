package dev.bpmcrafters.example.order.fulfillment.inventory.application.port.out;

import java.util.List;

/**
 * Port to access external inventory.
 */
public interface InventoryOutPort {

  /**
   * Requests goods to be fetched and reserved for a customer order.
   * @param orderId customer order id.
   * @param items list of items to be fetched.
   * @return flag indicating if goods could be fetched.
   */
  boolean fetchGoods(String orderId, List<InventoryItemDto> items);
}
