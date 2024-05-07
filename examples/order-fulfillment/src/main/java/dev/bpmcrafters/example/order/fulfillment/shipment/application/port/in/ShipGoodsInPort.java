package dev.bpmcrafters.example.order.fulfillment.shipment.application.port.in;

import dev.bpmcrafters.example.order.fulfillment.order.domain.Address;

import java.util.UUID;

public interface ShipGoodsInPort {

  void shipGoods(UUID orderId, Address shippingAddress);
}
