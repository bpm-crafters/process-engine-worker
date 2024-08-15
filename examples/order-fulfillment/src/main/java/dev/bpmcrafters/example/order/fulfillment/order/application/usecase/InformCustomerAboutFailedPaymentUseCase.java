package dev.bpmcrafters.example.order.fulfillment.order.application.usecase;

import dev.bpmcrafters.example.order.fulfillment.order.application.port.in.InformCustomerAboutFailedPaymentInPort;
import dev.bpmcrafters.example.order.fulfillment.order.application.port.out.UserTaskOutPort;
import dev.bpmcrafters.example.order.fulfillment.order.domain.Order;
import dev.bpmcrafters.example.order.fulfillment.order.domain.PaymentProblem;
import dev.bpmcrafters.processengine.worker.registrar.VariableConverter;
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd;
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;

/**
 * Use case to inform the customer about payment problems.
 */
@Component
@RequiredArgsConstructor
public class InformCustomerAboutFailedPaymentUseCase implements InformCustomerAboutFailedPaymentInPort {

  private final UserTaskOutPort userTaskOutPort;
  private final UserTaskCompletionApi completionApi;
  private final VariableConverter variableConverter;

  @Override
  public PaymentProblem loadFailedPaymentDetails(String taskId) {
    var payload = Objects.requireNonNull(userTaskOutPort.getVariables(taskId), "Could not load task " + taskId);
    var order = variableConverter.mapToType(payload.get("order"), Order.class);
    var rejectionReason = variableConverter.mapToType(payload.get("paymentFailedReason"), String.class);
    var paymentAmount = variableConverter.mapToType(payload.get("paymentAmount"), BigDecimal.class)
      .setScale(2, RoundingMode.HALF_EVEN);

    return new PaymentProblem(
      order.orderId().toString(),
      rejectionReason,
      paymentAmount
    );
  }

  @Override
  @SneakyThrows
  public void confirmFailedPayment(String taskId) {
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
