package dev.bpmcrafters.example.order.fulfillment.order.infrastructure;

import dev.bpmcrafters.example.order.fulfillment.inventory.infrastructure.InventoryConfiguration;
import dev.bpmcrafters.example.order.fulfillment.payment.infrastructure.PaymentConfiguration;
import dev.bpmcrafters.example.order.fulfillment.shipment.infrastructure.ShipmentConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({
  InventoryConfiguration.class,
  OrderConfiguration.class,
  PaymentConfiguration.class,
  ShipmentConfiguration.class
})
public class FulfillmentProcessApplication {
  public static void main(String[] args) {
    SpringApplication.run(FulfillmentProcessApplication.class, args);
  }
}
