package dev.bpmcrafters.example.order.fulfillment.order.application.port.in;

import dev.bpmcrafters.example.order.fulfillment.order.domain.InventoryDelay;

/**
 * Use case port for informing customers about delayed inventory.
 */
public interface InformCustomerAboutDelayedInventoryInPort {
  /**
   * Load details about delayed inventory.
   *
   * @param taskId id of the user task.
   * @return description of the delay.
   */
  InventoryDelay loadDelayedInventoryDetails(String taskId);

  /**
   * Confirm delayed inventory.
   * @param taskId id of the user task.
   */
  void confirmDelayedInventory(String taskId);
}
