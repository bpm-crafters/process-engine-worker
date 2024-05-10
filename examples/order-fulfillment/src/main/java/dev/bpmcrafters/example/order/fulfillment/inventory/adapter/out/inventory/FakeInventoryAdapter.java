package dev.bpmcrafters.example.order.fulfillment.inventory.adapter.out.inventory;

import dev.bpmcrafters.example.order.fulfillment.inventory.application.port.out.InventoryItemDto;
import dev.bpmcrafters.example.order.fulfillment.inventory.application.port.out.InventoryOutPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fake adapter for inventory.
 */
@Component
@Slf4j
public class FakeInventoryAdapter implements InventoryOutPort {

  @Override
  public boolean fetchGoods(String orderId, List<InventoryItemDto> items) {
    log.info("[INVENTORY ADAPTER] Fetching goods for {}]", orderId);
    var inStock = inStock(items);
    log.info("[INVENTORY ADAPTER] Items are{}in stock", inStock ? " " : "not ");
    return inStock;
  }

  /**
   * We have everything at stock, only "out-of-stock" items is not there.
   *
   * @param items items to check.
   * @return true if all items can be delivered.
   */
  private boolean inStock(List<InventoryItemDto> items) {
    return items.stream().map(InventoryItemDto::name).filter(n -> n.equals("out-of-stock")).findAny().isEmpty();
  }

}
