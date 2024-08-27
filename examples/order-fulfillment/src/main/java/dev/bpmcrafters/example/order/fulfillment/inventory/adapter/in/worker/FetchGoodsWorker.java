package dev.bpmcrafters.example.order.fulfillment.inventory.adapter.in.worker;

import dev.bpmcrafters.example.order.fulfillment.inventory.application.port.in.FetchGoodsInPort;
import dev.bpmcrafters.example.order.fulfillment.order.domain.Order;
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengine.worker.Variable;
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd;
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi;
import dev.bpmcrafters.processengineapi.task.TaskInformation;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * In-bound adapter called by process engine.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FetchGoodsWorker {

  private final FetchGoodsInPort fetchGoodsInPort;

  @ProcessEngineWorker(topic = "fetchGoods", autoComplete = false)
  @SneakyThrows
  public void fetchGoods(
    TaskInformation taskInformation,
    ServiceTaskCompletionApi externalTaskCompletionApi,
    @Variable(name = "order") Order order
  ) {
    log.info("EXAMPLE: <Worker> Received task {}", taskInformation.getTaskId());

    boolean inStock = fetchGoodsInPort.fetchGoods(order);

    if (inStock) {
      log.info("Example: <Worker> Goods are in stock.");
      externalTaskCompletionApi.completeTask(
        new CompleteTaskCmd(
          taskInformation.getTaskId(),
          () -> Map.of("shipped", true)
        )
      ).get();
    } else {
      log.info("EXAMPLE: <Worker> Goods are NOT in stock.");
    }
  }
}
