package dev.bpmcrafters.example.order.fulfillment.shipment.infrastructure;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("dev.bpmcrafters.example.order.fulfillment.shipment")
@Slf4j
public class ShipmentConfiguration {

  @PostConstruct
  public void reportActivation() {
    log.info("[STARTUP]: SHIPMENT MODULE ACTIVATED");
  }
}
