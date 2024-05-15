package dev.bpmcrafters.example.order.fulfillment.inventory.application.port.in;

import dev.bpmcrafters.example.order.fulfillment.order.domain.Order;

/**
 * Application in-bound port to trigger inventory goods fetching.
 */
public interface FetchGoodsInPort {

  /**
   * Fetches goods from inventory.
   * @param order order contaniing the details.
   * @return flag if the goods are fetched.
   */
  boolean fetchGoods(Order order);

}
