package dev.bpmcrafters.processengine.worker.registrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengine.worker.Variable;
import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerProperties;
import dev.bpmcrafters.processengine.worker.idempotency.NoOpIdempotencyRegistry;
import dev.bpmcrafters.processengine.worker.registrar.metrics.ProcessEngineWorkerMetricsNoOp;
import dev.bpmcrafters.processengineapi.task.*;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SpringProxyInvocationTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void invokesManagedJdkAndCglibProxiesUsingInterfaceMetadata(final boolean classProxy) {
        final var subscriptions = mock(TaskSubscriptionApi.class);
        final var completion = mock(ServiceTaskCompletionApi.class);
        when(subscriptions.subscribeForTask(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(completion.completeTask(any())).thenReturn(CompletableFuture.completedFuture(null));
        final var interceptions = new AtomicInteger();
        final var worker = new Worker();
        final var factory = new ProxyFactory(worker);
        factory.setProxyTargetClass(classProxy);
        factory.addAdvice((MethodInterceptor) invocation -> {
            interceptions.incrementAndGet();
            return invocation.proceed();
        });
        final var proxy = factory.getProxy();
        final var registrar = new ProcessEngineStarterRegistrar(new ProcessEngineWorkerProperties(),
                subscriptions, completion, new JacksonVariableConverter(new ObjectMapper()),
                ParameterResolver.builder().build(), ResultResolver.builder().build(),
                mock(TransactionTemplate.class), ProcessEngineWorkerMetricsNoOp.INSTANCE, new NoOpIdempotencyRegistry());

        assertThat(registrar.postProcessAfterInitialization(proxy, "worker")).isSameAs(proxy);
        final var command = ArgumentCaptor.forClass(SubscribeForTaskCmd.class);
        verify(subscriptions).subscribeForTask(command.capture());
        assertThat(command.getValue().getTaskDescriptionKey()).isEqualTo("proxy-topic");
        command.getValue().getAction().accept(new TaskInformation("task", Map.of()), Map.of("input", "hello"));
        assertThat(interceptions.get()).isEqualTo(1);
        assertThat(worker.input).isEqualTo("hello");
        verify(completion).completeTask(any());
    }

    public interface WorkerContract {
        @ProcessEngineWorker("proxy-topic")
        void work(@Variable("input") String input);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void registersCovariantWorkerOnceAndRetainsPayload(final boolean classProxy) {
        assertPayloadThroughProxy(new CovariantWorker(), classProxy, Map.of(), Map.of("result", "covariant"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void convertsGenericInterfaceParameterAndRetainsPayload(final boolean classProxy) {
        assertPayloadThroughProxy(new GenericWorker(), classProxy, Map.of("number", "42"), Map.of("number", 42));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void convertsGenericArrayParameterWithoutRegisteringUnrelatedArrayOverload(final boolean classProxy) {
        assertPayloadThroughProxy(new GenericArrayWorker(), classProxy,
                Map.of("values", List.of("1", "2")), Map.of("sum", 3));
    }

    private void assertPayloadThroughProxy(final Object worker, final boolean classProxy,
                                          final Map<String, Object> payload, final Map<String, Object> expected) {
        final var subscriptions = mock(TaskSubscriptionApi.class);
        final var completion = mock(ServiceTaskCompletionApi.class);
        when(subscriptions.subscribeForTask(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(completion.completeTask(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(completion.failTask(any())).thenReturn(CompletableFuture.completedFuture(null));
        final var interceptions = new AtomicInteger();
        final var factory = new ProxyFactory(worker);
        factory.setProxyTargetClass(classProxy);
        factory.addAdvice((MethodInterceptor) invocation -> {
            interceptions.incrementAndGet();
            return invocation.proceed();
        });
        final var registrar = new ProcessEngineStarterRegistrar(new ProcessEngineWorkerProperties(),
                subscriptions, completion, new JacksonVariableConverter(new ObjectMapper()),
                ParameterResolver.builder().build(), ResultResolver.builder().build(),
                mock(TransactionTemplate.class), ProcessEngineWorkerMetricsNoOp.INSTANCE, new NoOpIdempotencyRegistry());
        registrar.postProcessAfterInitialization(factory.getProxy(), "worker");
        final var command = ArgumentCaptor.forClass(SubscribeForTaskCmd.class);
        verify(subscriptions, times(1)).subscribeForTask(command.capture());
        command.getValue().getAction().accept(new TaskInformation("task", Map.of()), payload);
        assertThat(interceptions.get()).isEqualTo(1);
        final var completed = ArgumentCaptor.forClass(CompleteTaskCmd.class);
        verify(completion).completeTask(completed.capture());
        assertThat(completed.getValue().get()).containsExactlyInAnyOrderEntriesOf(expected);
    }

    public interface CovariantContract {
        @ProcessEngineWorker("covariant") Object work();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void keepsDistinctCovariantWorkersWhileDeduplicatingTheirBridges(final boolean classProxy) {
        final var subscriptions = mock(TaskSubscriptionApi.class);
        final var completion = mock(ServiceTaskCompletionApi.class);
        when(subscriptions.subscribeForTask(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(completion.completeTask(any())).thenReturn(CompletableFuture.completedFuture(null));
        final var factory = new ProxyFactory(new MultipleWorker());
        factory.setProxyTargetClass(classProxy);
        final var registrar = new ProcessEngineStarterRegistrar(new ProcessEngineWorkerProperties(),
                subscriptions, completion, new JacksonVariableConverter(new ObjectMapper()),
                ParameterResolver.builder().build(), ResultResolver.builder().build(),
                mock(TransactionTemplate.class), ProcessEngineWorkerMetricsNoOp.INSTANCE, new NoOpIdempotencyRegistry());
        registrar.postProcessAfterInitialization(factory.getProxy(), "multiple-worker");
        final var commands = ArgumentCaptor.forClass(SubscribeForTaskCmd.class);
        verify(subscriptions, times(2)).subscribeForTask(commands.capture());
        assertThat(commands.getAllValues()).extracting(SubscribeForTaskCmd::getTaskDescriptionKey)
                .containsExactlyInAnyOrder("first", "second");
        for (final var command : commands.getAllValues()) {
            command.getAction().accept(new TaskInformation(command.getTaskDescriptionKey(), Map.of()), Map.of());
        }
        final var completed = ArgumentCaptor.forClass(CompleteTaskCmd.class);
        verify(completion, times(2)).completeTask(completed.capture());
        for (final var command : completed.getAllValues()) {
            assertThat(command.get()).containsEntry("worker", command.getTaskId());
        }
    }

    public interface MultipleContract {
        @ProcessEngineWorker("first") Object first();
        @ProcessEngineWorker("second") Object second();
    }
    public static class MultipleWorker implements MultipleContract {
        @Override public Map<String, Object> first() { return Map.of("worker", "first"); }
        @Override public Map<String, Object> second() { return Map.of("worker", "second"); }
    }
    public static class CovariantWorker implements CovariantContract {
        @Override public Map<String, Object> work() { return Map.of("result", "covariant"); }
    }
    public interface GenericContract<T, R> {
        @ProcessEngineWorker("generic") R work(@Variable("number") T number);
    }
    public interface SpecializedContract<T> extends GenericContract<T, Map<String, Object>> {}
    public static class GenericWorker implements SpecializedContract<Integer> {
        @Override public Map<String, Object> work(final Integer number) { return Map.of("number", number); }
        public Map<String, Object> work(final String unrelated) { throw new AssertionError("Unannotated overload"); }
    }

    public interface GenericArrayContract<T> {
        @ProcessEngineWorker("generic-array") Map<String, Object> work(@Variable("values") T[] values);
    }
    public static class GenericArrayWorker implements GenericArrayContract<Integer> {
        @Override public Map<String, Object> work(final Integer[] values) {
            assertThat(values).containsExactly(1, 2);
            return Map.of("sum", values[0] + values[1]);
        }
        public Map<String, Object> work(final String[] unrelated) { throw new AssertionError("Unannotated array overload"); }
    }

    public static class Worker implements WorkerContract {
        private String input;
        @Override public void work(final String input) { this.input = input; }
    }
}
