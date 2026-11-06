package dev.bpmcrafters.example.quarkus.adapter.in.worker;

import dev.bpmcrafters.example.quarkus.adapter.OrderMapper;
import dev.bpmcrafters.example.quarkus.adapter.OrderWire;
import dev.bpmcrafters.example.quarkus.application.port.in.FulfillOrder;
import dev.bpmcrafters.example.quarkus.domain.PaymentResult;
import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred;
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengine.worker.Variable;
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd;
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi;
import dev.bpmcrafters.processengineapi.task.TaskInformation;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

@ApplicationScoped
public class FulfillmentWorkers {
  private final FulfillOrder fulfillment;

  public FulfillmentWorkers(FulfillOrder fulfillment) { this.fulfillment = fulfillment; }

  @ProcessEngineWorker(topic = "retrievePayment")
  public Map<String, Object> receivePayment(@Variable(name = "order") OrderWire order) throws BpmnErrorOccurred {
    final PaymentResult result = fulfillment.retrievePayment(new FulfillOrder.Command(OrderMapper.toDomain(order)));
    if (!result.received()) {
      throw new BpmnErrorOccurred("Payment rejected", "paymentFailed", Map.of(
        "paymentReceived", false, "paymentFailedReason", result.failureReason(), "paymentAmount", result.amount()));
    }
    return Map.of("paymentReceived", true, "paymentId", result.reference());
  }

  @ProcessEngineWorker(topic = "fetchGoods", autoComplete = false)
  public void fetchGoods(TaskInformation task, ServiceTaskCompletionApi completion, @Variable(name = "order") OrderWire order) throws Exception {
    try {
      fulfillment.fetchGoods(new FulfillOrder.Command(OrderMapper.toDomain(order)));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw exception;
    }
    completion.completeTask(new CompleteTaskCmd(task.getTaskId(), Map.of("shipped", true))).get();
  }

  @ProcessEngineWorker(topic = "shipGoods")
  public Map<String, Object> shipGoods(@Variable(name = "order") OrderWire order) {
    fulfillment.shipGoods(new FulfillOrder.Command(OrderMapper.toDomain(order)));
    return Map.of("goodsShipped", true);
  }
}
