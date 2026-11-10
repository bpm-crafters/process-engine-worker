package dev.bpmcrafters.example.quarkus.adapter;

import dev.bpmcrafters.example.quarkus.adapter.in.worker.FulfillmentWorkers;
import dev.bpmcrafters.example.quarkus.application.port.in.FulfillOrder;
import dev.bpmcrafters.example.quarkus.domain.PaymentResult;
import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred;
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengine.worker.Variable;
import dev.bpmcrafters.processengineapi.Empty;
import dev.bpmcrafters.processengineapi.task.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import static dev.bpmcrafters.example.quarkus.DomainTestData.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FulfillmentWorkersTest {
  private final FulfillOrder fulfillment = mock(FulfillOrder.class);
  private final FulfillmentWorkers workers = new FulfillmentWorkers(fulfillment);

  @Test
  void mapsPaymentSuccessAndCheckedBpmnErrorExactly() throws Exception {
    final var order = order();
    final var wire = OrderMapper.toWire(order);
    when(fulfillment.retrievePayment(any())).thenReturn(PaymentResult.forOrder(order));
    assertThat(workers.receivePayment(wire)).containsExactlyInAnyOrderEntriesOf(Map.of("paymentReceived", true, "paymentId", order.orderId().toString()));
    final var rejected = order("21.00", "book");
    when(fulfillment.retrievePayment(any())).thenReturn(PaymentResult.forOrder(rejected));
    final BpmnErrorOccurred error = catchThrowableOfType(() -> workers.receivePayment(OrderMapper.toWire(rejected)), BpmnErrorOccurred.class);
    assertThat(error).hasMessage("Payment rejected");
    assertThat(error.getErrorCode()).isEqualTo("paymentFailed");
    assertThat(error.getPayload()).containsExactlyInAnyOrderEntriesOf(Map.of("paymentReceived", false,
      "paymentFailedReason", PaymentResult.REJECTION_REASON, "paymentAmount", rejected.total()));
    assertThat(FulfillmentWorkers.class.getMethod("receivePayment", OrderWire.class).getExceptionTypes()).contains(BpmnErrorOccurred.class);
  }

  @Test
  void inventoryCompletesExactlyOnceManuallyAndShippingKeepsDistinctOutput() throws Exception {
    final var completion = mock(ServiceTaskCompletionApi.class);
    when(completion.completeTask(any())).thenAnswer(call -> {
      final CompleteTaskCmd command = call.getArgument(0);
      assertThat(command.getTaskId()).isEqualTo("job");
      assertThat(command.get()).containsExactlyInAnyOrderEntriesOf(Map.of("shipped", true));
      return CompletableFuture.completedFuture(Empty.INSTANCE);
    });
    workers.fetchGoods(new TaskInformation("job", Map.of()), completion, OrderMapper.toWire(order()));
    verify(completion, times(1)).completeTask(any());
    assertThat(workers.shipGoods(OrderMapper.toWire(order()))).containsExactlyInAnyOrderEntriesOf(Map.of("goodsShipped", true));
    verify(fulfillment).shipGoods(any());
    final var method = FulfillmentWorkers.class.getMethod("fetchGoods", TaskInformation.class, ServiceTaskCompletionApi.class, OrderWire.class);
    assertThat(method.getAnnotation(ProcessEngineWorker.class).autoComplete()).isFalse();
    for (final var worker : FulfillmentWorkers.class.getDeclaredMethods()) {
      if (!worker.isAnnotationPresent(ProcessEngineWorker.class)) continue;
      final var parameter = java.util.Arrays.stream(worker.getParameters()).filter(p -> p.getType() == OrderWire.class).findFirst().orElseThrow();
      assertThat(parameter.getAnnotation(Variable.class).name()).isEqualTo("order");
      assertThat(parameter.getAnnotation(Variable.class).mandatory()).isTrue();
    }
  }

  @Test
  void waitFailureNeverCompletesAndInterruptionIsRestored() throws Exception {
    final var completion = mock(ServiceTaskCompletionApi.class);
    doThrow(new ArithmeticException("overflow")).when(fulfillment).fetchGoods(any());
    assertThatThrownBy(() -> workers.fetchGoods(new TaskInformation("job", Map.of()), completion, OrderMapper.toWire(order())))
      .isInstanceOf(ArithmeticException.class);
    verifyNoInteractions(completion);
    doThrow(new InterruptedException("stop")).when(fulfillment).fetchGoods(any());
    try {
      assertThatThrownBy(() -> workers.fetchGoods(new TaskInformation("job", Map.of()), completion, OrderMapper.toWire(order())))
        .isInstanceOf(InterruptedException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      verifyNoInteractions(completion);
    } finally {
      Thread.interrupted();
    }
  }
}
