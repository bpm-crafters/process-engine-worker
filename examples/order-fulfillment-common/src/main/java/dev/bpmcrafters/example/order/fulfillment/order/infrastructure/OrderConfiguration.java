package dev.bpmcrafters.example.order.fulfillment.order.infrastructure;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("dev.bpmcrafters.example.order.fulfillment.order")
@Slf4j
public class OrderConfiguration {
  @PostConstruct
  public void reportActivation() {
    log.info("EXAMPLE: Order context activated.");
  }
}
