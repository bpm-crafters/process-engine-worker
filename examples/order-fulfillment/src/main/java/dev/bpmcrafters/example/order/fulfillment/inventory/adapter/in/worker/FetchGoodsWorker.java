package dev.bpmcrafters.example.order.fulfillment.inventory.adapter.in.worker;

import dev.bpmcrafters.example.order.fulfillment.inventory.application.port.in.FetchGoodsInPort;
import dev.bpmcrafters.example.order.fulfillment.order.domain.Order;
import dev.bpmcrafters.example.order.fulfillment.order.domain.OrderPosition;
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengine.worker.Variable;
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd;
import dev.bpmcrafters.processengineapi.task.ExternalTaskCompletionApi;
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

  @ProcessEngineWorker(topic = "fetchGoods")
  @SneakyThrows
  public void fetchGoods(
    TaskInformation taskInformation,
    ExternalTaskCompletionApi externalTaskCompletionApi,
    @Variable(name = "order") Order order
  ) {

    boolean inStock = fetchGoodsInPort.fetchGoods(order);

    if (inStock) {
      externalTaskCompletionApi.completeTask(
        new CompleteTaskCmd(
          taskInformation.getTaskId(),
          () -> Map.of("shipped", true)
        )
      ).get();
    }
  }
}
