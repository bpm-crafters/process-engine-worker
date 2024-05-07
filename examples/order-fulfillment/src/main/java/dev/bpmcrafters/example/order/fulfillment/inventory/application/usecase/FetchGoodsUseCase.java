package dev.bpmcrafters.example.order.fulfillment.inventory.application.usecase;

import dev.bpmcrafters.example.order.fulfillment.inventory.application.port.in.FetchGoodsInPort;
import dev.bpmcrafters.example.order.fulfillment.order.domain.Order;
import dev.bpmcrafters.example.order.fulfillment.order.domain.OrderPosition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FetchGoodsUseCase implements FetchGoodsInPort {

  @Override
  public boolean fetchGoods(Order order) {
    log.info("[INVENTORY]: Fetching goods for {}", order.orderId());
    return inStock(order);
  }

  /**
   * We have everything at stock, only "out-of-stock" position is not there.
   *
   * @param order order to check.
   * @return true if order positions can be delivered.
   */
  private boolean inStock(Order order) {
    return order.orderPositions().stream().map(OrderPosition::name).filter(n -> n.equals("out-of-stock")).findAny().isEmpty();
  }

}
