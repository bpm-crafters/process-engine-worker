package dev.bpmcrafters.example.order.fulfillment.inventory.adapter.out.inventory;

import dev.bpmcrafters.example.order.fulfillment.inventory.application.port.out.InventoryItemDto;
import dev.bpmcrafters.example.order.fulfillment.inventory.application.port.out.InventoryOutPort;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fake adapter for inventory.
 */
@Component
@Slf4j
public class FakeInventoryAdapter implements InventoryOutPort {

  private static final String OUT_OF_STOCK = "out-of-stock";
  private static final Long STEP = 10_000L;
  private static final Long DELIVERY_TIMEOUT = STEP * 6;


  @Override
  public boolean fetchGoods(String orderId, List<InventoryItemDto> items) {
    log.info("[INVENTORY ADAPTER] Fetching goods for {}]", orderId);
    var inStock = inStock(items);
    if (!inStock) {
      log.info("[INVENTORY ADAPTER] Some items were not in stock. Wait {} seconds until these are delivered.", DELIVERY_TIMEOUT / 1000);
      inStock = orderAndDeliver();
      log.info("[INVENTORY ADAPTER] All items have been delivered with a delay.");
    }
    log.info("[INVENTORY ADAPTER] Items are{}in stock", inStock ? " " : " not ");
    return inStock;
  }

  /**
   * We have everything at stock, only "out-of-stock" items is not there.
   *
   * @param items items to check.
   * @return true if all items can be delivered.
   */
  private boolean inStock(List<InventoryItemDto> items) {
    return items.stream().map(InventoryItemDto::name).filter(n -> n.equals(OUT_OF_STOCK)).findAny().isEmpty();
  }

  /**
   * If item is not in stock, we can re-order and deliver it to stock. This takes some time.
   *
   * @return status if in stock.
   */
  @SneakyThrows
  private boolean orderAndDeliver() {
    for (long i = 0; i < DELIVERY_TIMEOUT; i = i + STEP) {
      Thread.sleep(STEP);
      log.info("[INVENTORY ADAPTER] {} / {}", i, DELIVERY_TIMEOUT);
    }

    return true;
  }

}
