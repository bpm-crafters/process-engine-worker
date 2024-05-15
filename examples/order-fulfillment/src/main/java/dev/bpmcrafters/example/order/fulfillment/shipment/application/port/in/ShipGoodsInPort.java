package dev.bpmcrafters.example.order.fulfillment.shipment.application.port.in;

import dev.bpmcrafters.example.order.fulfillment.order.domain.Address;

import java.util.UUID;

/**
 * Application inbound port to trigger good shipping.
 */
public interface ShipGoodsInPort {

  /**
   * Ship goods to designated address.
   * @param orderId order id.
   * @param shippingAddress address to ship to.
   */
  void shipGoods(UUID orderId, Address shippingAddress);
}
