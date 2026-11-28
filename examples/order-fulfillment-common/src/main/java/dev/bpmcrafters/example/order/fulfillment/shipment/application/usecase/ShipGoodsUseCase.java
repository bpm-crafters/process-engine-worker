package dev.bpmcrafters.example.order.fulfillment.shipment.application.usecase;

import dev.bpmcrafters.example.order.fulfillment.order.domain.Address;
import dev.bpmcrafters.example.order.fulfillment.shipment.application.port.in.ShipGoodsInPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fake use case implementation for good shipment.
 */
@Component
@Slf4j
public class ShipGoodsUseCase implements ShipGoodsInPort {

  @Override
  public void shipGoods(UUID orderId, Address shippingAddress) {

    log.info("EXAMPLE: <SHIPPING> Shipping goods for order {} to {}", orderId, shippingAddress);
  }
}
