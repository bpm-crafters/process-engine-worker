package dev.bpmcrafters.example.order.fulfillment.infrastructure;

import dev.bpmcrafters.example.order.fulfillment.adapter.AdapterConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(AdapterConfiguration.class)
public class FulfillmentProcessApplication {

  public static void main(String[] args) {
    SpringApplication.run(FulfillmentProcessApplication.class, args);
  }
}
