package dev.bpmcrafters.example.quarkus.application.usecase;

import dev.bpmcrafters.example.quarkus.application.port.in.FulfillOrder.Command;
import dev.bpmcrafters.example.quarkus.application.port.out.FulfillmentServices;
import dev.bpmcrafters.example.quarkus.domain.Order;
import dev.bpmcrafters.example.quarkus.domain.PaymentResult;
import org.junit.jupiter.api.Test;
import static dev.bpmcrafters.example.quarkus.DomainTestData.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FulfillOrderUseCaseTest {
  private final FulfillmentServices services = mock(FulfillmentServices.class);
  private final FulfillOrderUseCase useCase = new FulfillOrderUseCase(services);

  @Test
  void delegatesPaymentAndShippingToServices() {
    final Order order = order();
    final PaymentResult result = PaymentResult.forOrder(order);
    when(services.collectPayment(order)).thenReturn(result);
    assertThat(useCase.retrievePayment(new Command(order))).isSameAs(result);
    useCase.shipGoods(new Command(order));
    verify(services).ship(order);
  }

  @Test
  void onlyOutOfStockOrdersWaitAndFailuresPropagate() throws Exception {
    useCase.fetchGoods(new Command(order()));
    verifyNoInteractions(services);
    final Order delayed = order("1", "out-of-stock");
    useCase.fetchGoods(new Command(delayed));
    verify(services).replenish(delayed);
    doThrow(new InterruptedException("stop")).when(services).replenish(delayed);
    assertThatThrownBy(() -> useCase.fetchGoods(new Command(delayed))).isInstanceOf(InterruptedException.class);
  }
}
