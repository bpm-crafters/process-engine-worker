package dev.bpmcrafters.example.order.fulfillment.order.infrastructure;

import dev.bpmcrafters.processengineapi.adapter.c8.springboot.C8AdapterProperties;
import io.camunda.tasklist.CamundaTaskListClient;
import io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties;
import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("dev.bpmcrafters.example.order.fulfillment.order")
@Slf4j
public class OrderConfiguration {
  @PostConstruct
  public void reportActivation() {
    log.info("[STARTUP]: ORDER MODULE ACTIVATED");
  }


  @Bean
  @SneakyThrows
  public CamundaTaskListClient camundaTaskListClientSaaS(
    ZeebeClientConfigurationProperties zeebeClientCloudConfigurationProperties,
    C8AdapterProperties c8AdapterProperties
  )  {
    return CamundaTaskListClient
      .builder()
      .taskListUrl(c8AdapterProperties.getUserTasks().getTasklistUrl())
      .saaSAuthentication(
        zeebeClientCloudConfigurationProperties.getCloud().getClientId(),
        zeebeClientCloudConfigurationProperties.getCloud().getClientSecret()
        )
      .shouldReturnVariables()
      .build();
  }

}
