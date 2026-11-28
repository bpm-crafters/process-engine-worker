package dev.bpmcrafters.example.order.fulfillment.order.infrastructure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(FulfillmentConfiguration.class)
public class FulfillmentProcessApplication {

  public static void main(String[] args) {
    SpringApplication.run(FulfillmentProcessApplication.class, args);
  }
}
