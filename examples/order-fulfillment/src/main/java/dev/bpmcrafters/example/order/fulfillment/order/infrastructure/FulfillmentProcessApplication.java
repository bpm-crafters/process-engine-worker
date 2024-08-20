package dev.bpmcrafters.example.order.fulfillment.order.infrastructure;

import dev.bpmcrafters.example.order.fulfillment.inventory.infrastructure.InventoryConfiguration;
import dev.bpmcrafters.example.order.fulfillment.payment.infrastructure.PaymentConfiguration;
import dev.bpmcrafters.example.order.fulfillment.shipment.infrastructure.ShipmentConfiguration;
import io.camunda.zeebe.spring.client.testsupport.SpringZeebeTestContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

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


  @Bean
  @Profile("c7embedded") // workaround for https://github.com/camunda-community-hub/spring-zeebe/issues/905
  public SpringZeebeTestContext fakeTestContext() {
    return new SpringZeebeTestContext();
  }

}
