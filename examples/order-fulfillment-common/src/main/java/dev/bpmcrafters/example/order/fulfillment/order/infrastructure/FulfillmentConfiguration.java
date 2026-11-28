package dev.bpmcrafters.example.order.fulfillment.order.infrastructure;

import dev.bpmcrafters.example.order.fulfillment.inventory.infrastructure.InventoryConfiguration;
import dev.bpmcrafters.example.order.fulfillment.payment.infrastructure.PaymentConfiguration;
import dev.bpmcrafters.example.order.fulfillment.shipment.infrastructure.ShipmentConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({
  InventoryConfiguration.class,
  OrderConfiguration.class,
  PaymentConfiguration.class,
  ShipmentConfiguration.class
})
public class FulfillmentConfiguration {
}
