package dev.bpmcrafters.example.quarkus.application.usecase;

import dev.bpmcrafters.example.quarkus.application.port.in.ReceiveOrder.Command;
import dev.bpmcrafters.example.quarkus.application.port.out.OrderPublisher;
import org.junit.jupiter.api.Test;
import static dev.bpmcrafters.example.quarkus.DomainTestData.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReceiveOrderUseCaseTest {
  @Test
  void publishesOriginalOrderAndDoesNotHideFailure() throws Exception {
    final OrderPublisher publisher = mock(OrderPublisher.class);
    final var order = order();
    final var useCase = new ReceiveOrderUseCase(publisher);
    useCase.receive(new Command(order));
    verify(publisher).publish(order);
    doThrow(new IllegalStateException("offline")).when(publisher).publish(order);
    assertThatThrownBy(() -> useCase.receive(new Command(order))).hasMessage("offline");
  }
}
