package dev.bpmcrafters.example.order.fulfillment.order.application.usecase;

import dev.bpmcrafters.example.order.fulfillment.order.application.port.in.ReceiveOrderInPort;
import dev.bpmcrafters.example.order.fulfillment.order.domain.Order;
import dev.bpmcrafters.processengineapi.process.StartProcessApi;
import dev.bpmcrafters.processengineapi.process.StartProcessByMessageCmd;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReceiveOrderUseCase implements ReceiveOrderInPort {

  private final StartProcessApi startProcessApi;

  @Override
  @SneakyThrows
  public void orderReceived(Order order) {
    startProcessApi.startProcess(
      new StartProcessByMessageCmd(
        "Msg_OrderReceived",
        () -> Map.of("order", order)
      )
    ).get();
  }
}
