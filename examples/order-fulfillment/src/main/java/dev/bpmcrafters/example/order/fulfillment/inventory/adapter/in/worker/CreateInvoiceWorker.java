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

  /**
   * Creats the invoice.
   * @param order order to create invoice for.
   * @param taskInformation task information.
   * @return order (mind the return type, we registered a special result resolver strategy in the infrastructure)
   */
  @ProcessEngineWorker(topic = "createInvoice")
  @SneakyThrows
  public Order createInvoice(@Variable(name = "order") Order order, TaskInformation taskInformation) {
    log.info("EXAMPLE: <Worker> Creating Invoice received {}", taskInformation.getTaskId());
    Thread.sleep(10_000L); // take a nap
    log.info("EXAMPLE: <Worker> Invoice created.");
    return order;
  }
}
