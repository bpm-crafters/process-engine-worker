package dev.bpmcrafters.example.order.fulfillment.order.application.usecase;

import dev.bpmcrafters.example.order.fulfillment.order.application.port.in.InformCustomerAboutDelayedInventoryInPort;
import dev.bpmcrafters.example.order.fulfillment.order.application.port.out.UserTaskOutPort;
import dev.bpmcrafters.example.order.fulfillment.order.domain.InventoryDelay;
import dev.bpmcrafters.example.order.fulfillment.order.domain.Order;
import dev.bpmcrafters.example.order.fulfillment.order.domain.OrderPosition;
import dev.bpmcrafters.processengine.worker.converter.VariableConverter;
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd;
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * Use case to inform customer about delays in the inventory.
 */
@Component
@RequiredArgsConstructor
public class InformCustomerAboutDelayedInventoryUseCase implements InformCustomerAboutDelayedInventoryInPort {

  private final UserTaskOutPort userTaskOutPort;
  private final UserTaskCompletionApi completionApi;
  private final VariableConverter variableConverter;

  @Override
  public InventoryDelay loadDelayedInventoryDetails(String taskId) {
    var payload = Objects.requireNonNull(userTaskOutPort.getVariables(taskId), "Could not load task " + taskId);
    var order = variableConverter.mapToType(payload.get("order"), Order.class);
    return new InventoryDelay(
      order.orderId().toString(),
      order.orderPositions().stream().map(OrderPosition::name).toList()
    );
  }

  @Override
  @SneakyThrows
  public void confirmDelayedInventory(String taskId) {
    completionApi.completeTask(
      new CompleteTaskCmd(
        taskId,
        () -> Map.of(
          "customer-informed", true
        )
      )
    ).get();
  }
}
