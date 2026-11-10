package dev.bpmcrafters.example.quarkus.application.usecase;

import dev.bpmcrafters.example.quarkus.application.port.in.ManageNotifications.Command;
import dev.bpmcrafters.example.quarkus.application.port.out.NotificationCompletion;
import dev.bpmcrafters.example.quarkus.application.port.out.NotificationStore;
import dev.bpmcrafters.example.quarkus.domain.Notification;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static dev.bpmcrafters.example.quarkus.DomainTestData.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationUseCaseTest {
  private final NotificationStore store = mock(NotificationStore.class);
  private final NotificationCompletion completion = mock(NotificationCompletion.class);
  private final NotificationUseCase useCase = new NotificationUseCase(store, completion);

  @Test
  void intakeTerminationAndLexicalQueryUseStorePort() {
    final var task = failed("10");
    useCase.accept(task);
    verify(store).putIfAbsent(task);
    useCase.terminate("10");
    verify(store).remove("10");
    when(store.all()).thenReturn(List.of(failed("2"), task, delayed("1")));
    assertThat(useCase.list()).extracting(Notification::taskId).containsExactly("1", "10", "2");
  }

  @Test
  void validatesTypeAndExistenceBeforeCompletion() throws Exception {
    when(store.find("known")).thenReturn(Optional.of(failed("known")));
    when(store.find("unknown")).thenReturn(Optional.empty());
    assertThat(useCase.find(new Command("known", Notification.Type.FAILED_PAYMENT))).isPresent();
    assertThat(useCase.find(new Command("known", Notification.Type.DELAYED_INVENTORY))).isEmpty();
    assertThat(useCase.complete(new Command("known", Notification.Type.DELAYED_INVENTORY))).isFalse();
    assertThat(useCase.complete(new Command("unknown", Notification.Type.FAILED_PAYMENT))).isFalse();
    verifyNoInteractions(completion);
    verify(store, never()).remove(anyString(), any());
  }

  @Test
  void completionAwaitsSuccessBeforeConditionalRemovalAndRetainsOnFailure() throws Exception {
    final var task = failed("1");
    when(store.find("1")).thenReturn(Optional.of(task));
    final var command = new Command("1", task.type());
    doThrow(new IllegalStateException("offline")).when(completion).complete("1");
    assertThatThrownBy(() -> useCase.complete(command)).hasMessage("offline");
    verify(store, never()).remove(anyString(), any());
    clearInvocations(store, completion);
    doNothing().when(completion).complete("1");
    assertThat(useCase.complete(command)).isTrue();
    final var ordered = inOrder(completion, store);
    ordered.verify(completion).complete("1");
    ordered.verify(store).remove("1", task);
  }

  @Test
  void synchronousTerminationDuringEngineCompletionMakesSubsequentRemovalHarmless() throws Exception {
    final var task = delayed("1");
    when(store.find("1")).thenReturn(Optional.of(task));
    doAnswer(call -> { useCase.terminate("1"); return null; }).when(completion).complete("1");
    assertThat(useCase.complete(new Command("1", task.type()))).isTrue();
    verify(store).remove("1");
    verify(store).remove("1", task);
  }
}
