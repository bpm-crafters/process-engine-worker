package dev.bpmcrafters.example.order.fulfillment.inventory.infrastructure;

import dev.bpmcrafters.example.order.fulfillment.order.domain.Order;
import dev.bpmcrafters.processengine.worker.registrar.ResultResolver;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ComponentScan("dev.bpmcrafters.example.order.fulfillment.inventory")
@Slf4j
public class InventoryConfiguration {

  @PostConstruct
  public void reportActivation() {
    log.info("EXAMPLE: Inventory context activated");
  }

  @Bean
  public ResultResolver myResultResolver() {
    // special strategy demonstrating the mapping of a custom return type to the payload for task completion.
    return ResultResolver
      .builder()
      .addStrategy(new ResultResolver.ResultResolutionStrategy(
        (method) -> method.getReturnType().equals(Order.class),
        (result) -> Map.of("invoice", ((Order) result).invoiceAddress())
      ))
      .build();
  }
}
