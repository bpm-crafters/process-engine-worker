package dev.bpmcrafters.example.order.fulfillment.payment.infrastructure;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("dev.bpmcrafters.example.order.fulfillment.payment")
@Slf4j
public class PaymentConfiguration {

  @PostConstruct
  public void reportActivation() {
    log.info("[STARTUP] PAYMENT MODULE ACTIVATED");
  }
}
