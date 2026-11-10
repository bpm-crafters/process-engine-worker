package dev.bpmcrafters.example.quarkus.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bpmcrafters.example.quarkus.adapter.out.camunda.*;
import dev.bpmcrafters.example.quarkus.adapter.out.simulation.ExampleFulfillmentServices;
import dev.bpmcrafters.processengineapi.Empty;
import dev.bpmcrafters.processengineapi.deploy.*;
import dev.bpmcrafters.processengineapi.process.*;
import dev.bpmcrafters.processengineapi.task.*;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static dev.bpmcrafters.example.quarkus.DomainTestData.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CamundaAdaptersTest {
  @Test
  void orderWireRoundTripKeepsOnlyContractFields() throws Exception {
    final var mapper = new ObjectMapper();
    final var original = order();
    final String json = mapper.writeValueAsString(OrderMapper.toWire(original));
    assertThat(mapper.readTree(json).properties()).extracting(Map.Entry::getKey)
      .containsExactlyInAnyOrder("orderId", "orderPositions", "shippingAddress", "invoiceAddress");
    assertThat(OrderMapper.toDomain(mapper.readValue(json, OrderWire.class)).total()).isEqualTo(original.total());
    final var converter = new dev.bpmcrafters.processengine.worker.registrar.JacksonVariableConverter(mapper);
    final Object payload = mapper.readValue(json, Map.class);
    assertThat(OrderMapper.toDomain(converter.mapToType(payload, OrderWire.class)).orderId()).isEqualTo(original.orderId());
  }

  @Test
  void messageStartHasNoInventedInstanceOrBusinessKey() throws Exception {
    final var api = mock(StartProcessApi.class);
    final var order = order();
    when(api.startProcess(any())).thenAnswer(call -> {
      final StartProcessByMessageCmd command = call.getArgument(0);
      assertThat(command.getMessageName()).isEqualTo("Msg_OrderReceived");
      assertThat(command.getRestrictions()).isEmpty();
      assertThat(command.get()).containsExactlyInAnyOrderEntriesOf(Map.of("order", OrderMapper.toWire(order)));
      return CompletableFuture.completedFuture(new ProcessInformation("", Map.of()));
    });
    new CamundaOrderPublisher(api).publish(order);
    verify(api).startProcess(any());
  }

  @Test
  void userCompletionWaitsForExactPublishedCommand() throws Exception {
    final var api = mock(UserTaskCompletionApi.class);
    when(api.completeTask(any())).thenAnswer(call -> {
      final CompleteTaskCmd command = call.getArgument(0);
      assertThat(command.getTaskId()).isEqualTo("task");
      assertThat(command.get()).containsExactlyInAnyOrderEntriesOf(Map.of("customer-informed", true));
      return CompletableFuture.completedFuture(Empty.INSTANCE);
    });
    new CamundaNotificationCompletion(api).complete("task");
    verify(api).completeTask(any());
    doReturn(CompletableFuture.failedFuture(new IllegalStateException("offline"))).when(api).completeTask(any());
    assertThatThrownBy(() -> new CamundaNotificationCompletion(api).complete("task")).hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  void deploymentKeepsSharedResourceOpenUntilFutureCompletes() throws Exception {
    final var api = mock(DeploymentApi.class);
    final var result = new CompletableFuture<DeploymentInformation>();
    final var captured = new CompletableFuture<InputStream>();
    when(api.deploy(any())).thenAnswer(call -> {
      final DeployBundleCommand command = call.getArgument(0);
      assertThat(command.getTenantId()).isNull();
      assertThat(command.getResources()).hasSize(1);
      final NamedResource resource = command.getResources().get(0);
      assertThat(resource.getName()).isEqualTo("C8/Order Fulfillment.bpmn");
      captured.complete(resource.getResourceStream());
      return result;
    });
    final var execution = CompletableFuture.supplyAsync(() -> {
      try { return new CamundaProcessDeployment(api).deploy(); }
      catch (Exception exception) { throw new java.util.concurrent.CompletionException(exception); }
    });
    try {
      final InputStream stream = captured.get(10, TimeUnit.SECONDS);
      assertThat(execution).isNotDone();
      try (final InputStream expected = getClass().getClassLoader().getResourceAsStream("C8/Order Fulfillment.bpmn")) {
        assertThat(stream.readAllBytes()).isEqualTo(expected.readAllBytes());
      }
      result.complete(new DeploymentInformation("deployment", null, null));
      assertThat(execution.get(10, TimeUnit.SECONDS).deploymentKey()).isEqualTo("deployment");
      assertThatThrownBy(stream::read).isInstanceOf(java.io.IOException.class);
    } finally {
      result.completeExceptionally(new IllegalStateException("cleanup"));
    }
  }

  @Test
  void simulatedWaitUsesExactMillisecondsAndNeverHidesOverflow() throws Exception {
    final var order = order();
    new ExampleFulfillmentServices(0).replenish(order);
    new ExampleFulfillmentServices(0).ship(order);
    assertThat(new ExampleFulfillmentServices(0).collectPayment(order).received()).isTrue();
    assertThatThrownBy(() -> new ExampleFulfillmentServices(Long.MAX_VALUE).replenish(order)).isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> new ExampleFulfillmentServices(-1).replenish(order)).isInstanceOf(IllegalArgumentException.class);
  }
}
