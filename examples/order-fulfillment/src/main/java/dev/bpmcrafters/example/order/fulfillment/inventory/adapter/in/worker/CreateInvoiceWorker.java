package dev.bpmcrafters.example.order.fulfillment.inventory.adapter.in.worker;

import dev.bpmcrafters.example.order.fulfillment.order.domain.Order;
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengine.worker.Variable;
import dev.bpmcrafters.processengineapi.task.TaskInformation;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class CreateInvoiceWorker {

  @ProcessEngineWorker(topic = "createInvoice")
  @SneakyThrows
  public Map<String, Object> createInvoice(@Variable(name = "order") Order order, TaskInformation taskInformation) {
    log.info("INVOICE: Creating Invoice for {}", taskInformation.getTaskId());
    Thread.sleep(10_000L); // take a nap
    return Map.of("invoice", order.invoiceAddress());
  }
}
