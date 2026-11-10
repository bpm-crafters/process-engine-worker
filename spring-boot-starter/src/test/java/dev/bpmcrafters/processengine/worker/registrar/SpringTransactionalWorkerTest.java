package dev.bpmcrafters.processengine.worker.registrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerProperties;
import dev.bpmcrafters.processengine.worker.idempotency.InMemoryIdempotencyRegistry;
import dev.bpmcrafters.processengine.worker.registrar.metrics.ProcessEngineWorkerMetricsNoOp;
import dev.bpmcrafters.processengineapi.CommonRestrictions;
import dev.bpmcrafters.processengineapi.task.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

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

    @Test
    void inheritedJdkWorkerCompletesBeforeInterfaceTransactionCommits() {
        final List<String> committedWrites = new ArrayList<>();
        registerWorker(new InheritedBeforeWorker(events, committedWrites), false);
        when(completion.completeTask(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(registry.getTaskResult(task)).isNull();
            assertThat(committedWrites).isEmpty();
            events.add("complete");
            return CompletableFuture.completedFuture(null);
        });
        singleCommand().getAction().accept(task, Map.of());
        assertThat(events).containsExactly("begin", "worker", "complete", "commit");
        assertThat(registry.getTaskResult(task)).containsEntry("result", "ok");
        assertThat(committedWrites).containsExactly("saved");
        verify(completion, never()).failTask(any());
    }

    @Test
    void inheritedJdkWorkerRollsBackWhenBeforeCommitCompletionFails() {
        final List<String> committedWrites = new ArrayList<>();
        registerWorker(new InheritedBeforeWorker(events, committedWrites), false);
        when(completion.completeTask(any())).thenAnswer(invocation -> {
            events.add("failed-completion");
            return CompletableFuture.failedFuture(new IllegalStateException("completion failed"));
        });
        singleCommand().getAction().accept(task, Map.of());
        assertThat(events).containsExactly("begin", "worker", "failed-completion", "rollback");
        assertThat(registry.getTaskResult(task)).isNull();
        assertThat(committedWrites).isEmpty();
        final var failed = ArgumentCaptor.forClass(FailTaskCmd.class);
        verify(completion).failTask(failed.capture());
        assertThat(failed.getValue().getReason()).isEqualTo("completion failed");
    }

    private SubscribeForTaskCmd singleCommand() {
        final var captured = ArgumentCaptor.forClass(SubscribeForTaskCmd.class);
        verify(subscriptions).subscribeForTask(captured.capture());
        return captured.getValue();
    }

    public interface InheritedBeforeContract {
        @Transactional
        @ProcessEngineWorker(value = "inherited-before", completion = ProcessEngineWorker.Completion.BEFORE_COMMIT)
        Map<String, Object> work();
    }

    public static class InheritedBeforeBase {
        private final List<String> events;
        private final List<String> committedWrites;

        public InheritedBeforeBase(final List<String> events, final List<String> committedWrites) {
            this.events = events;
            this.committedWrites = committedWrites;
        }

        public Map<String, Object> work() {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            events.add("worker");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { committedWrites.add("saved"); }
            });
            return Map.of("result", "ok");
        }
    }

    public static class InheritedBeforeWorker extends InheritedBeforeBase implements InheritedBeforeContract {
        public InheritedBeforeWorker(final List<String> events, final List<String> committedWrites) {
            super(events, committedWrites);
        }
    }

    @ParameterizedTest
    @MethodSource("effectiveTransactionCases")
    void usesEffectiveTransactionAttribute(final Object worker, final boolean transactional, final boolean classProxy) {
        registerWorker(worker, classProxy);
        when(completion.completeTask(any())).thenReturn(CompletableFuture.completedFuture(null));
        final var captured = ArgumentCaptor.forClass(SubscribeForTaskCmd.class);
        verify(subscriptions).subscribeForTask(captured.capture());
        captured.getValue().getAction().accept(task, Map.of());
        final var completed = ArgumentCaptor.forClass(CompleteTaskCmd.class);
        verify(completion).completeTask(completed.capture());
        assertThat(completed.getValue().get()).containsEntry("transactional", transactional);
        assertThat(events).containsExactlyElementsOf(transactional ? List.of("begin", "commit") : List.of());
        if (transactional) assertThat(manager.lookups).isEqualTo(2);
        verify(completion, never()).failTask(any());
    }

    static Stream<Arguments> effectiveTransactionCases() {
        return Stream.of(false, true).flatMap(classProxy -> Stream.of(
                Arguments.of(new NeverOverride(), false, classProxy),
                Arguments.of(new NotSupportedOverride(), false, classProxy),
                Arguments.of(new ClassNeverOverride(), false, classProxy),
                Arguments.of(new RequiredOverride(), true, classProxy),
                Arguments.of(new JakartaNeverOverride(), false, classProxy),
                Arguments.of(new ComposedOverride(), true, classProxy),
                Arguments.of(new TransactionalSubclass(), false, classProxy)));
    }

    public interface RequiredContract {
        @Transactional @ProcessEngineWorker("precedence") Map<String, Object> work();
    }
    public static class NeverOverride implements RequiredContract {
        @Override @Transactional(propagation = Propagation.NEVER)
        public Map<String, Object> work() { return transactionState(false); }
    }
    public static class NotSupportedOverride implements RequiredContract {
        @Override @Transactional(propagation = Propagation.NOT_SUPPORTED)
        public Map<String, Object> work() { return transactionState(false); }
    }
    @Transactional
    public static class ClassNeverOverride implements RequiredContract {
        @Override @Transactional(propagation = Propagation.NEVER)
        @ProcessEngineWorker("precedence")
        public Map<String, Object> work() { return transactionState(false); }
    }
    public interface NeverContract {
        @Transactional(propagation = Propagation.NEVER)
        @ProcessEngineWorker("precedence") Map<String, Object> work();
    }
    public static class RequiredOverride implements NeverContract {
        @Override @Transactional
        public Map<String, Object> work() { return transactionState(true); }
    }
    @jakarta.transaction.Transactional
    public static class JakartaNeverOverride implements JakartaRequiredContract {
        @Override @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.NEVER)
        public Map<String, Object> work() { return transactionState(false); }
    }
    public interface JakartaRequiredContract {
        @jakarta.transaction.Transactional @ProcessEngineWorker("precedence") Map<String, Object> work();
    }
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) @Transactional
    public @interface WorkerTransaction {}
    public static class ComposedOverride implements NeverContract {
        @Override @WorkerTransaction
        public Map<String, Object> work() { return transactionState(true); }
    }
    public interface InheritedContract {
        @ProcessEngineWorker("precedence") Map<String, Object> work();
    }
    public static class NonTransactionalBase {
        public Map<String, Object> work() { return transactionState(false); }
    }
    @Transactional
    public static class TransactionalSubclass extends NonTransactionalBase implements InheritedContract {}

    private static Map<String, Object> transactionState(final boolean expected) {
        final var active = TransactionSynchronizationManager.isActualTransactionActive();
        assertThat(active).isEqualTo(expected);
        return Map.of("transactional", active);
    }

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
        registerWorker(new Worker(events), classProxy);
    }

    private void registerWorker(final Object worker, final boolean classProxy) {
        when(subscriptions.subscribeForTask(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(completion.failTask(any())).thenReturn(CompletableFuture.completedFuture(null));
        final var factory = new ProxyFactory(worker);
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
