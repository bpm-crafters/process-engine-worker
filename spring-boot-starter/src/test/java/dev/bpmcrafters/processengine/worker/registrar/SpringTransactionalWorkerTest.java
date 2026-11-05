package dev.bpmcrafters.processengine.worker.registrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerProperties;
import dev.bpmcrafters.processengine.worker.idempotency.InMemoryIdempotencyRegistry;
import dev.bpmcrafters.processengine.worker.registrar.metrics.ProcessEngineWorkerMetricsNoOp;
import dev.bpmcrafters.processengineapi.CommonRestrictions;
import dev.bpmcrafters.processengineapi.task.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SpringTransactionalWorkerTest {
    private final List<String> events = new ArrayList<>();
    private final LocalTransactionManager manager = new LocalTransactionManager(events);
    private final InMemoryIdempotencyRegistry registry = new InMemoryIdempotencyRegistry();
    private final TaskSubscriptionApi subscriptions = mock(TaskSubscriptionApi.class);
    private final ServiceTaskCompletionApi completion = mock(ServiceTaskCompletionApi.class);
    private final TaskInformation task = new TaskInformation("task", Map.of(CommonRestrictions.PROCESS_INSTANCE_ID, "process"));

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void completesBeforeCommitAndPublishesResultOnlyAfterCommit(final boolean classProxy) {
        register(classProxy);
        when(completion.completeTask(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(registry.getTaskResult(task)).isNull();
            events.add("complete");
            return CompletableFuture.completedFuture(null);
        });
        command("before").getAction().accept(task, Map.of());
        assertThat(events).containsExactly("begin", "worker", "complete", "commit");
        assertThat(registry.getTaskResult(task)).containsEntry("result", "ok");
        assertThat(manager.lookups).isEqualTo(2);
        verify(completion, never()).failTask(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void completesAfterCommitWithCommittedResult(final boolean classProxy) {
        register(classProxy);
        when(completion.completeTask(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(registry.getTaskResult(task)).containsEntry("result", "ok");
            events.add("complete");
            return CompletableFuture.completedFuture(null);
        });
        command("after").getAction().accept(task, Map.of());
        assertThat(events).containsExactly("begin", "worker", "commit", "complete");
        assertThat(manager.lookups).isEqualTo(2);
        verify(completion, never()).failTask(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failingWorkerRollsBackWithoutCompletionOrCachedResult(final boolean classProxy) {
        register(classProxy);
        command("failing").getAction().accept(task, Map.of());
        assertThat(events).containsExactly("begin", "worker", "rollback");
        assertThat(manager.lookups).isEqualTo(2);
        assertFailure("worker failed");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void commitFailurePropagatesToFailureReportingAndDiscardsPendingResult(final boolean classProxy) {
        register(classProxy);
        manager.failCommit = true;
        command("after").getAction().accept(task, Map.of());
        assertThat(events).containsExactly("begin", "worker", "commit", "rollback");
        assertFailure("commit failed");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedBeforeCommitCompletionRollsBackAndDiscardsPendingResult(final boolean classProxy) {
        register(classProxy);
        when(completion.completeTask(any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("completion failed")));
        command("before").getAction().accept(task, Map.of());
        assertThat(events).containsExactly("begin", "worker", "rollback");
        assertThat(registry.getTaskResult(task)).isNull();
        final var failed = ArgumentCaptor.forClass(FailTaskCmd.class);
        verify(completion).failTask(failed.capture());
        assertThat(failed.getValue().getReason()).isEqualTo("completion failed");
    }

    private void assertFailure(final String reason) {
        assertThat(registry.getTaskResult(task)).isNull();
        verify(completion, never()).completeTask(any());
        final var failed = ArgumentCaptor.forClass(FailTaskCmd.class);
        verify(completion).failTask(failed.capture());
        assertThat(failed.getValue().getReason()).isEqualTo(reason);
    }

    private void register(final boolean classProxy) {
        when(subscriptions.subscribeForTask(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(completion.failTask(any())).thenReturn(CompletableFuture.completedFuture(null));
        final var factory = new ProxyFactory(new Worker(events));
        factory.setProxyTargetClass(classProxy);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        final var properties = new ProcessEngineWorkerProperties();
        properties.setRemoveTaskResultOnCompletion(false);
        final var registrar = new ProcessEngineStarterRegistrar(properties, subscriptions, completion,
                new JacksonVariableConverter(new ObjectMapper()), ParameterResolver.builder().build(),
                ResultResolver.builder().build(), new TransactionTemplate(manager),
                ProcessEngineWorkerMetricsNoOp.INSTANCE, registry);
        registrar.postProcessAfterInitialization(factory.getProxy(), "transactional-worker");
    }

    private SubscribeForTaskCmd command(final String topic) {
        final var captor = ArgumentCaptor.forClass(SubscribeForTaskCmd.class);
        verify(subscriptions, times(3)).subscribeForTask(captor.capture());
        return captor.getAllValues().stream().filter(command -> command.getTaskDescriptionKey().equals(topic))
                .findFirst().orElseThrow();
    }

    public interface Contract {
        Map<String, Object> before();
        @Transactional
        @ProcessEngineWorker(value = "after", completion = ProcessEngineWorker.Completion.AFTER_COMMIT)
        Map<String, Object> after();
        Map<String, Object> failing();
    }

    public static class Worker implements Contract {
        private final List<String> events;
        public Worker(final List<String> events) { this.events = events; }

        @Override @Transactional
        @ProcessEngineWorker(value = "before", completion = ProcessEngineWorker.Completion.BEFORE_COMMIT)
        public Map<String, Object> before() { return result(); }

        @Override
        public Map<String, Object> after() { return result(); }

        @Override @Transactional
        @ProcessEngineWorker("failing")
        public Map<String, Object> failing() {
            result();
            throw new IllegalStateException("worker failed");
        }

        private Map<String, Object> result() {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            events.add("worker");
            return Map.of("result", "ok");
        }
    }

    private static class LocalTransactionManager extends AbstractPlatformTransactionManager {
        private final ThreadLocal<State> state = ThreadLocal.withInitial(State::new);
        private final List<String> events;
        private boolean failCommit;
        private int lookups;

        LocalTransactionManager(final List<String> events) {
            this.events = events;
            setRollbackOnCommitFailure(true);
        }

        @Override protected Object doGetTransaction() { lookups++; return state.get(); }
        @Override protected boolean isExistingTransaction(final Object transaction) { return ((State) transaction).active; }
        @Override protected void doBegin(final Object transaction, final TransactionDefinition definition) {
            ((State) transaction).active = true;
            events.add("begin");
        }
        @Override protected void doCommit(final DefaultTransactionStatus status) {
            events.add("commit");
            if (failCommit) throw new TransactionSystemException("commit failed");
        }
        @Override protected void doRollback(final DefaultTransactionStatus status) { events.add("rollback"); }
        @Override protected void doSetRollbackOnly(final DefaultTransactionStatus status) {
            ((State) status.getTransaction()).rollbackOnly = true;
        }
        @Override protected void doCleanupAfterCompletion(final Object transaction) { state.remove(); }
    }

    private static class State implements SmartTransactionObject {
        private boolean active;
        private boolean rollbackOnly;
        @Override public boolean isRollbackOnly() { return rollbackOnly; }
        @Override public void flush() {}
    }
}
