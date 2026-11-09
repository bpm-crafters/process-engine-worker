package dev.bpmcrafters.example.quarkus.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bpmcrafters.example.quarkus.adapter.in.worker.NotificationIntake;
import dev.bpmcrafters.example.quarkus.application.port.in.ManageNotifications;
import dev.bpmcrafters.processengineapi.adapter.c8.task.delivery.SubscribingRefreshingZeebeJobUserTaskDelivery;
import dev.bpmcrafters.processengineapi.adapter.c8.task.delivery.SubscribingServiceTaskDelivery;
import dev.bpmcrafters.processengineapi.adapter.c8.task.subscription.C8TaskSubscriptionApiImpl;
import dev.bpmcrafters.processengineapi.impl.task.InMemSubscriptionRepository;
import dev.bpmcrafters.processengineapi.task.*;
import io.camunda.client.CamundaClient;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CamundaLifecycleTest {
  @SuppressWarnings("unchecked")
  private <T> Instance<T> instance(T value) {
    final Instance<T> instance = mock(Instance.class);
    when(instance.get()).thenReturn(value);
    return instance;
  }

  @SuppressWarnings("unchecked")
  private <T> Instance<T> forbidden() {
    return mock(Instance.class, call -> { throw new AssertionError("Disabled lifecycle resolved " + call.getMethod()); });
  }

  private NotificationIntake intake() { return new NotificationIntake(new ObjectMapper(), mock(ManageNotifications.class)); }

  @Test
  void disabledLifecycleNeverResolvesEngineBeansEvenWithComposedApplicationServices() throws Exception {
    final var composition = new ApplicationComposition();
    final var notifications = composition.notifications(new dev.bpmcrafters.example.quarkus.adapter.out.memory.InMemoryNotificationStore(),
      task -> { throw new AssertionError("Unused completion resolved"); });
    final var lifecycle = new CamundaDeliveryLifecycle(false, new NotificationIntake(new ObjectMapper(), notifications), forbidden(), forbidden(), forbidden());
    lifecycle.register(new StartupEvent());
    lifecycle.activate(new StartupEvent());
    lifecycle.stop(new ShutdownEvent());
    new CamundaBridge().closeAfterDrain(new ShutdownEvent());
  }

  @Test
  void ownedClientClosesOnceAcrossShutdownAndDisposerFallback() {
    final var client = mock(CamundaClient.class);
    final var bridge = new CamundaBridge();
    bridge.closeOwned();
    bridge.own(client);
    bridge.own(client);
    bridge.closeAfterDrain(new ShutdownEvent());
    bridge.dispose(client);
    bridge.closeOwned();
    verify(client, times(1)).close();
  }

  @Test
  void bothClientsCloseOnceAfterDrainAndRepeatedDisposers() {
    final var service = mock(CamundaClient.class);
    final var notification = mock(CamundaClient.class);
    final var bridge = new CamundaBridge();
    bridge.own(service);
    bridge.own(notification);
    verifyNoInteractions(service, notification);
    bridge.closeAfterDrain(new ShutdownEvent());
    bridge.dispose(service);
    bridge.disposeNotifications(notification);
    bridge.closeAfterDrain(new ShutdownEvent());
    verify(service, times(1)).close();
    verify(notification, times(1)).close();
  }

  @Test
  void productionClientsKeepServiceAndNotificationExecutionPoolsSeparate() {
    final var bridge = new CamundaBridge();
    try {
      final var service = bridge.client(new ObjectMapper(), "http://127.0.0.1:1", "http://127.0.0.1:2");
      final var notification = bridge.notificationClient(new ObjectMapper(), "http://127.0.0.1:1", "http://127.0.0.1:2");
      assertThat(service).isNotSameAs(notification);
      assertThat(service.getConfiguration().getNumJobWorkerExecutionThreads()).isEqualTo(4);
      assertThat(notification.getConfiguration().getNumJobWorkerExecutionThreads()).isEqualTo(1);
    } finally {
      bridge.closeOwned();
    }
  }

  @Test
  void eitherQualifiedDisposerClosesBothClientsWithoutShutdownEvent() {
    for (final boolean notificationsFirst : List.of(false, true)) {
      final var service = mock(CamundaClient.class);
      final var notification = mock(CamundaClient.class);
      final var bridge = new CamundaBridge();
      bridge.own(service);
      bridge.own(notification);
      if (notificationsFirst) bridge.disposeNotifications(notification);
      else bridge.dispose(service);
      bridge.dispose(service);
      bridge.disposeNotifications(notification);
      verify(service, times(1)).close();
      verify(notification, times(1)).close();
    }
  }

  @Test
  void closeFailureDoesNotLeakOtherClientOrRetryAndSuppressesDistinctFailure() {
    final var service = mock(CamundaClient.class);
    final var notification = mock(CamundaClient.class);
    final var first = new IllegalStateException("service failed");
    final var second = new IllegalStateException("notification failed");
    doThrow(first).when(service).close();
    doThrow(second).when(notification).close();
    final var bridge = new CamundaBridge();
    bridge.own(service);
    bridge.own(notification);
    assertThatThrownBy(() -> bridge.closeAfterDrain(new ShutdownEvent())).isSameAs(first).hasSuppressedException(second);
    bridge.dispose(service);
    bridge.disposeNotifications(notification);
    verify(service, times(1)).close();
    verify(notification, times(1)).close();
  }

  @Test
  void sharedCloseErrorIsNotSelfSuppressedAndErrorsStillCloseAll() {
    final var first = mock(CamundaClient.class);
    final var second = mock(CamundaClient.class);
    final var failure = new AssertionError("close");
    doThrow(failure).when(first).close();
    doThrow(failure).when(second).close();
    final var bridge = new CamundaBridge();
    bridge.own(first);
    bridge.own(second);
    assertThatThrownBy(bridge::closeOwned).isSameAs(failure);
    assertThat(failure.getSuppressed()).isEmpty();
    verify(second).close();
    bridge.closeOwned();
  }

  @Test
  void concurrentOwnershipAndShutdownCloseEachIdentityOnce() throws Exception {
    final var clients = List.of(mock(CamundaClient.class), mock(CamundaClient.class));
    final var bridge = new CamundaBridge();
    CompletableFuture.allOf(clients.stream().map(client -> CompletableFuture.runAsync(() -> bridge.own(client)))
      .toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
    CompletableFuture.allOf(clients.stream().map(client -> CompletableFuture.runAsync(() -> { bridge.own(client); bridge.closeOwned(); }))
      .toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
    clients.forEach(client -> verify(client, times(1)).close());
  }

  private Method bridgeMethod(String name) {
    return Arrays.stream(CamundaBridge.class.getMethods()).filter(method -> method.getName().equals(name)).findFirst().orElseThrow();
  }

  @Test
  void notificationQualifierIsolatesProducerDeliveryCompletionAndDisposer() {
    assertThat(NotificationClient.class.isAnnotationPresent(Qualifier.class)).isTrue();
    assertThat(bridgeMethod("notificationClient").isAnnotationPresent(NotificationClient.class)).isTrue();
    assertThat(bridgeMethod("client").isAnnotationPresent(NotificationClient.class)).isFalse();
    for (final String method : List.of("userDelivery", "userCompletion", "disposeNotifications")) {
      assertThat(bridgeMethod(method).getParameters()[0].isAnnotationPresent(NotificationClient.class)).as(method).isTrue();
    }
    for (final String method : List.of("serviceDelivery", "serviceCompletion", "startProcess", "deployment", "dispose")) {
      assertThat(bridgeMethod(method).getParameters()[0].isAnnotationPresent(NotificationClient.class)).as(method).isFalse();
    }
    for (final String method : List.of("dispose", "disposeNotifications")) {
      assertThat(bridgeMethod(method).getParameters()[0].isAnnotationPresent(Disposes.class)).isTrue();
    }
  }

  @Test
  void deliveryStartsAfterPoolAndThreeWorkersRegisterAndShutdownReleasesPool() throws Exception {
    final var repository = new InMemSubscriptionRepository();
    final var users = mock(SubscribingRefreshingZeebeJobUserTaskDelivery.class);
    final var services = mock(SubscribingServiceTaskDelivery.class);
    final TaskSubscriptionApi subscriptions = new C8TaskSubscriptionApiImpl(repository, users);
    final var lifecycle = new CamundaDeliveryLifecycle(true, intake(), instance(subscriptions), instance(services), instance(users));
    lifecycle.register(new StartupEvent());
    assertThat(repository.getTaskSubscriptions()).hasSize(1);
    verifyNoInteractions(services, users);
    for (final String topic : List.of("retrievePayment", "fetchGoods", "shipGoods")) {
      subscriptions.subscribeForTask(new SubscribeForTaskCmd(Map.of(), TaskType.EXTERNAL, topic, null,
        (task, payload) -> { }, (TaskTerminationHandler) task -> { })).get();
    }
    doAnswer(call -> { assertThat(repository.getTaskSubscriptions()).hasSize(4); return null; }).when(services).subscribe();
    lifecycle.activate(new StartupEvent());
    final var ordered = inOrder(services, users);
    ordered.verify(services).subscribe();
    ordered.verify(users).subscribe();
    lifecycle.stop(new ShutdownEvent());
    ordered.verify(users).unsubscribeAll();
    assertThat(repository.getTaskSubscriptions()).hasSize(3);
  }

  @Test
  void subscriptionIsReleasedEvenWhenUserUnsubscribeFails() throws Exception {
    final var repository = new InMemSubscriptionRepository();
    final var users = mock(SubscribingRefreshingZeebeJobUserTaskDelivery.class);
    final TaskSubscriptionApi subscriptions = new C8TaskSubscriptionApiImpl(repository, users);
    final var lifecycle = new CamundaDeliveryLifecycle(true, intake(), instance(subscriptions),
      instance(mock(SubscribingServiceTaskDelivery.class)), instance(users));
    lifecycle.register(new StartupEvent());
    lifecycle.activate(new StartupEvent());
    doThrow(new IllegalStateException("unsubscribe failed")).when(users).unsubscribeAll();
    assertThatThrownBy(() -> lifecycle.stop(new ShutdownEvent())).hasMessage("unsubscribe failed");
    assertThat(repository.getTaskSubscriptions()).isEmpty();
  }

  @Test
  void observerPrioritiesBracketExtensionStartupAndShutdown() throws Exception {
    assertThat(CamundaDeliveryLifecycle.class.getMethod("register", StartupEvent.class).getParameters()[0].getAnnotation(Priority.class).value()).isEqualTo(2400);
    assertThat(CamundaDeliveryLifecycle.class.getMethod("activate", StartupEvent.class).getParameters()[0].getAnnotation(Priority.class).value()).isEqualTo(3000);
    assertThat(CamundaDeliveryLifecycle.class.getMethod("stop", ShutdownEvent.class).getParameters()[0].getAnnotation(Priority.class).value()).isEqualTo(2000);
    assertThat(bridgeMethod("closeAfterDrain").getParameters()[0].getAnnotation(Priority.class).value()).isEqualTo(3000);
  }

  @Test
  void refreshExceptionDoesNotPreventNextCycle() {
    final var calls = new AtomicInteger();
    CamundaDeliveryLifecycle.refreshSafely(() -> { calls.incrementAndGet(); throw new IllegalStateException("temporary"); });
    CamundaDeliveryLifecycle.refreshSafely(calls::incrementAndGet);
    assertThat(calls).hasValue(2);
  }
}
