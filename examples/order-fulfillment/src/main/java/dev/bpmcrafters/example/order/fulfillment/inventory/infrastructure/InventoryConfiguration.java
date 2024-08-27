package dev.bpmcrafters.example.order.fulfillment.inventory.infrastructure;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("dev.bpmcrafters.example.order.fulfillment.inventory")
@Slf4j
public class InventoryConfiguration {

  @PostConstruct
  public void reportActivation() {
    log.info("EXAMPLE: Inventory context activated");
  }
}
