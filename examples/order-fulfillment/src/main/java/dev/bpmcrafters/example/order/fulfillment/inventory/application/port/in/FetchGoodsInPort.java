package dev.bpmcrafters.example.order.fulfillment.inventory.application.port.in;

import dev.bpmcrafters.example.order.fulfillment.order.domain.Order;

public interface FetchGoodsInPort {

  boolean fetchGoods(Order order);

}
