package dev.bpmcrafters.example.order.fulfillment.shipment.adapter.in.worker;

import dev.bpmcrafters.example.order.fulfillment.order.domain.Order;
import dev.bpmcrafters.example.order.fulfillment.shipment.application.port.in.ShipGoodsInPort;
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengine.worker.registrar.VariableConverter;
import dev.bpmcrafters.processengineapi.task.TaskInformation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class ShipGoodsWorker {

  private final ShipGoodsInPort shipGoodsInPort;
  private final VariableConverter variableConverter;

  @ProcessEngineWorker(topic = "shipGoods")
  public Map<String, Object> shipGoods(Map<String, Object> payload, TaskInformation taskInformation) {

    log.info("Received task {}", taskInformation.getTaskId());

    var order = variableConverter.mapToType(payload.get("order"), Order.class);
    shipGoodsInPort.shipGoods(order.orderId(), order.shippingAddress());
    return Map.of("goodsShipped", true);
  }
}
