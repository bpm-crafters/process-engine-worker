package dev.bpmcrafters.processengine.worker.registrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerAutoConfiguration;
import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry;
import dev.bpmcrafters.processengine.worker.idempotency.InMemoryIdempotencyRegistry;
import dev.bpmcrafters.processengine.worker.idempotency.NoOpIdempotencyRegistry;
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi;
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SpringRuntimeConfigurationTest {
    private final TaskSubscriptionApi subscriptions = mock(TaskSubscriptionApi.class);

    @Test
    void wiresDefaultsAndRegistersWorkersWhenEnabledByDefault() {
        try (final var context = context()) {
            context.refresh();
            assertThat(context.getBean(VariableConverter.class)).isInstanceOf(JacksonVariableConverter.class);
            assertThat(context.getBean(IdempotencyRegistry.class)).isInstanceOf(NoOpIdempotencyRegistry.class);
            verify(subscriptions).subscribeForTask(any());
        }
    }

    @Test
    void doesNotRegisterWorkersWhenDisabled() {
        try (final var context = context(false)) {
            context.refresh();
            assertThat(context.getBeansOfType(ProcessEngineStarterRegistrar.class)).isEmpty();
            verifyNoInteractions(subscriptions);
        }
    }

    @Test
    void preservesCustomResolverAndRegistryBeans() {
        try (final var context = context()) {
            final var resolver = ParameterResolver.builder().build();
            final var registry = new InMemoryIdempotencyRegistry();
            context.registerBean("customResolver", ParameterResolver.class, () -> resolver);
            context.registerBean("customRegistry", IdempotencyRegistry.class, () -> registry);
            context.refresh();
            assertThat(context.getBean(ParameterResolver.class)).isSameAs(resolver);
            assertThat(context.getBean(IdempotencyRegistry.class)).isSameAs(registry);
            verify(subscriptions).subscribeForTask(any());
        }
    }

    private AnnotationConfigApplicationContext context() {
        return context(true);
    }

    private AnnotationConfigApplicationContext context(final boolean enabled) {
        when(subscriptions.subscribeForTask(any())).thenReturn(CompletableFuture.completedFuture(null));
        final var context = new AnnotationConfigApplicationContext();
        if (!enabled) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "dev.bpm-crafters.process-api.worker.enabled", "false")));
        }
        context.registerBean(TaskSubscriptionApi.class, () -> subscriptions);
        context.registerBean(ServiceTaskCompletionApi.class, () -> mock(ServiceTaskCompletionApi.class));
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        context.registerBean(TransactionTemplate.class, () -> mock(TransactionTemplate.class));
        context.register(ProcessEngineWorkerAutoConfiguration.class, ProcessEngineStarterRegistrar.class);
        context.registerBean(Worker.class);
        return context;
    }

    public static class Worker {
        @ProcessEngineWorker("configured-worker") public void work() {}
    }
}
