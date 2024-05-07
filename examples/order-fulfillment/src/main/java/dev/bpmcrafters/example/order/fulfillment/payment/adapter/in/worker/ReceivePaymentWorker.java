package dev.bpmcrafters.example.order.fulfillment.payment.adapter.in.worker;

import dev.bpmcrafters.example.order.fulfillment.payment.application.port.in.PaymentFailedException;
import dev.bpmcrafters.example.order.fulfillment.payment.application.port.in.ReceivePaymentInPort;
import dev.bpmcrafters.example.order.fulfillment.order.domain.Order;
import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred;
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengine.worker.Variable;
import dev.bpmcrafters.processengineapi.task.TaskInformation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReceivePaymentWorker {

  private final ReceivePaymentInPort receivePaymentInPort;

  @ProcessEngineWorker(topic = "retrievePayment")
  public Map<String, Object> receivePayment(TaskInformation taskInformation, @Variable(name = "order") Order order) {

    log.info("Received task {}", taskInformation.getTaskId());

    try {
      var paymentId = receivePaymentInPort.receivePayment(order);
      return Map.of(
        "paymentReceived", true,
        "paymentId", paymentId
      );
    } catch (PaymentFailedException e) {
      throw new BpmnErrorOccurred("paymentFailed", "paymentFailed", Map.of(
        "paymentReceived", false,
        "paymentFailedReason", "Suspicious order total"
      ));
    }
  }


}
