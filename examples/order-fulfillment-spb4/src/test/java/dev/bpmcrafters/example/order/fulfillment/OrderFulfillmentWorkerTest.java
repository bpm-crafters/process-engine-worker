package dev.bpmcrafters.example.order.fulfillment;

import dev.bpmcrafters.example.order.fulfillment.order.domain.Address;
import dev.bpmcrafters.example.order.fulfillment.order.domain.Order;
import dev.bpmcrafters.example.order.fulfillment.order.domain.OrderPosition;
import dev.bpmcrafters.example.order.fulfillment.order.infrastructure.FulfillmentProcessApplication;
import io.camunda.client.CamundaClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.SpringApplication;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfSystemProperty(named = "orderFulfillmentSmokeTest", matches = "true")
class OrderFulfillmentWorkerTest {

  @Test
  void completesPaymentInventoryAndShippingJobs() {
    try (var application = SpringApplication.run(FulfillmentProcessApplication.class,
      "--spring.profiles.active=c8sm", "--server.port=0")) {
      var client = application.getBean(CamundaClient.class);
      var deployment = client.newDeployResourceCommand()
        .addResourceFromClasspath("C8/Order Fulfillment.bpmn")
        .requestTimeout(Duration.ofSeconds(15))
        .send().join();

      var address = new Address("221b Baker Street", "NW1 6XE", "London", "United Kingdom");
      var order = new Order(UUID.randomUUID(),
        List.of(new OrderPosition("Magnifying glass", 1, new BigDecimal("41.00"))), address, address);
      var result = client.newCreateInstanceCommand()
        .processDefinitionKey(deployment.getProcesses().getFirst().getProcessDefinitionKey())
        .startBeforeElement("Activity_RetrievePayment")
        .variable("order", order)
        .withResult()
        .requestTimeout(Duration.ofSeconds(30))
        .send().join();

      assertAll(
        () -> assertEquals(true, result.getVariable("paymentReceived"), "payment worker output"),
        () -> assertEquals(true, result.getVariable("shipped"), "inventory worker output"),
        () -> assertEquals(true, result.getVariable("goodsShipped"), "shipping worker output")
      );
    }
  }
}
