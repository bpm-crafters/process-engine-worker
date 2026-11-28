package dev.bpmcrafters.example.order.fulfillment.inventory.application.usecase;

import dev.bpmcrafters.example.order.fulfillment.inventory.application.port.in.FetchGoodsInPort;
import dev.bpmcrafters.example.order.fulfillment.inventory.application.port.out.InventoryItemDto;
import dev.bpmcrafters.example.order.fulfillment.inventory.application.port.out.InventoryOutPort;
import dev.bpmcrafters.example.order.fulfillment.order.domain.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Use case implementation to fetch goods from inventory.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FetchGoodsUseCase implements FetchGoodsInPort {

  private final InventoryOutPort inventoryOutPort;

  @Override
  public boolean fetchGoods(Order order) {
    if (order.orderPositions().isEmpty()) {
      throw new IllegalArgumentException("No order positions found");
    }
    return inventoryOutPort.fetchGoods(
      order.orderId().toString(),
      order.orderPositions().stream().map(p -> new InventoryItemDto(p.name(), p.amount())).toList()
    );
  }

}
