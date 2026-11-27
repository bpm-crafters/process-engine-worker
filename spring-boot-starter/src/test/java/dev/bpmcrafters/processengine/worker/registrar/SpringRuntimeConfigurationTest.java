package dev.bpmcrafters.processengine.worker.registrar;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerAutoConfiguration;
import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerDeploymentAutoConfiguration;
import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry;
import dev.bpmcrafters.processengine.worker.idempotency.InMemoryIdempotencyRegistry;
import dev.bpmcrafters.processengine.worker.idempotency.NoOpIdempotencyRegistry;
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi;
import dev.bpmcrafters.processengineapi.deploy.DeploymentInformation;
import dev.bpmcrafters.processengineapi.lifecycle.ProcessApplicationLifecycle;
import dev.bpmcrafters.processengineapi.lifecycle.ProcessApplicationLifecycleContext;
import dev.bpmcrafters.processengineapi.lifecycle.ProcessApplicationLifecycleParticipant;
import dev.bpmcrafters.processengineapi.lifecycle.ProcessApplicationPhase;
import dev.bpmcrafters.processengineapi.lifecycle.TaskSubscriptionRegistrationContext;
import dev.bpmcrafters.processengineapi.springboot.lifecycle.ProcessApplicationLifecycleAutoConfiguration;
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi;
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
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
            assertThat(context.getBean(VariableConverter.class)).isInstanceOf(Jackson2VariableConverter.class);
            assertThat(context.getBean(IdempotencyRegistry.class)).isInstanceOf(NoOpIdempotencyRegistry.class);
            verifyNoInteractions(subscriptions);
            final var registrationApi = mock(TaskSubscriptionApi.class);
            when(registrationApi.subscribeForTask(any())).thenReturn(CompletableFuture.completedFuture(null));
            context.getBean(ProcessApplicationLifecycle.class)
                    .transitionTo(ProcessApplicationPhase.RUNNING, new TaskSubscriptionRegistrationContext(registrationApi));
            verify(registrationApi).subscribeForTask(any());
            verifyNoInteractions(subscriptions);
        }
    }

    @Test
    void doesNotRegisterWorkersWhenDisabled() {
        try (final var context = context(false)) {
            context.refresh();
            startLifecycle(context);
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
            startLifecycle(context);
            verify(subscriptions).subscribeForTask(any());
        }
    }

    @Test
    void deploysWithAnApiConfiguredLaterAfterSubscriptionsAndBeforeInitialPull() {
        try (final var context = context()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("deployment", Map.of(
                    "dev.bpm-crafters.process-api.worker.deployment.enabled", "true",
                    "dev.bpm-crafters.process-api.worker.deployment.bpmn-resource-pattern",
                    "classpath:deployment-test.bpmn",
                    "dev.bpm-crafters.process-api.worker.deployment.dmn-resource-pattern",
                    "classpath*:deployment-test.dmn")));
            context.register(ProcessEngineWorkerDeploymentAutoConfiguration.class, LateDeploymentApiConfiguration.class);
            context.refresh();
            final var deployment = context.getBean(DeploymentApi.class);
            final var calls = inOrder(subscriptions, deployment);
            context.getBean(ProcessApplicationLifecycle.class).register(new ProcessApplicationLifecycleParticipant() {
                @Override
                public int getPhase() {
                    return ProcessApplicationPhase.SUBSCRIPTIONS_REGISTERED + 1;
                }

                @Override
                public void onPhase(final ProcessApplicationLifecycleContext phaseContext) {
                    calls.verify(subscriptions).subscribeForTask(any());
                    calls.verify(deployment).deploy(any());
                }
            });

            verifyNoInteractions(subscriptions, deployment);
            startLifecycle(context);
            startLifecycle(context);
            verifyNoMoreInteractions(subscriptions, deployment);
        }
    }

    private void startLifecycle(final AnnotationConfigApplicationContext context) {
        context.publishEvent(new ApplicationStartedEvent(new SpringApplication(), new String[0], context, Duration.ZERO));
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
        context.register(ProcessApplicationLifecycleAutoConfiguration.class,
                ProcessEngineWorkerAutoConfiguration.class, ProcessEngineStarterRegistrar.class);
        context.registerBean(Worker.class);
        return context;
    }

    public static class Worker {
        @ProcessEngineWorker("configured-worker") public void work() {}
    }

    @Configuration(proxyBeanMethods = false)
    static class LateDeploymentApiConfiguration {
        @Bean
        DeploymentApi deploymentApi() {
            final var api = mock(DeploymentApi.class);
            when(api.deploy(any())).thenReturn(CompletableFuture.completedFuture(
                    new DeploymentInformation("deployment", null, null)));
            return api;
        }
    }
}
