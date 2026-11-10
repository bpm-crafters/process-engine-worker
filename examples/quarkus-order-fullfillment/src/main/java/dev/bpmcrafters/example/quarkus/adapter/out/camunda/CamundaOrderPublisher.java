package dev.bpmcrafters.example.quarkus.adapter.out.camunda;

import dev.bpmcrafters.example.quarkus.adapter.OrderMapper;
import dev.bpmcrafters.example.quarkus.application.port.out.OrderPublisher;
import dev.bpmcrafters.example.quarkus.domain.Order;
import dev.bpmcrafters.processengineapi.process.StartProcessApi;
import dev.bpmcrafters.processengineapi.process.StartProcessByMessageCmd;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

@ApplicationScoped
public class CamundaOrderPublisher implements OrderPublisher {
  private final StartProcessApi start;

  public CamundaOrderPublisher(StartProcessApi start) { this.start = start; }

  @Override
  public void publish(Order order) throws Exception {
    start.startProcess(new StartProcessByMessageCmd("Msg_OrderReceived", Map.of("order", OrderMapper.toWire(order)), Map.of())).get();
  }
}
