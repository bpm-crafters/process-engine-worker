---
title: Process Engine Worker with Spring Boot
---

The Spring Boot starter integrates the framework-free worker core into Spring Boot: it auto-configures the required beans,
discovers worker methods on Spring beans, executes transactional workers with Spring's transaction management and
optionally deploys process resources on startup.

## Installation

Add the starter to your projects classpath. In Maven add the following to your `pom.xml`:

```xml
<dependency>
  <groupId>dev.bpm-crafters.process-engine-worker</groupId>
  <artifactId>process-engine-worker-spring-boot-starter</artifactId>
  <version>${process-engine-worker.version}</version>
</dependency>
```

In addition, a process engine adapter of Process Engine API (e.g. for Camunda 7 or Camunda 8) must be present, providing the
`TaskSubscriptionApi` and `ServiceTaskCompletionApi` beans.

The starter depends on `process-engine-worker-core` which contains the annotations and the worker logic. All classes keep their
package names (`dev.bpmcrafters.processengine.worker.**`), so upgrading from versions without the core module does not require
any changes to your code.

## Worker discovery

Workers are regular Spring beans (e.g. annotated with `@Component`). The starter registers a `BeanPostProcessor`
(`ProcessEngineStarterRegistrar`) which inspects every bean after its initialization for methods annotated with
`@ProcessEngineWorker` and subscribes them via the `TaskSubscriptionApi`. The target class of proxied beans is inspected, while
the invocation goes through the proxy, so AOP aspects (e.g. `@Transactional`) apply to the worker method.

```java
@Component
@RequiredArgsConstructor
public class MySmartWorker {

  private final FetchGoodsInPort fetchGoodsInPort;

  @ProcessEngineWorker(topic = "fetchGoods")
  public Map<String, Object> fetchGoods(
    @Variable(name = "order") Order order
  ) {
    var fetched = fetchGoodsInPort.fetchGoods(order);
    return Map.of("shipped", fetched);
  }
}
```

Since the registration happens during bean initialization, all workers are subscribed before the application is fully started
and before the adapters start their delivery.

## Auto-configured beans

The `ProcessEngineWorkerAutoConfiguration` provides the following beans. Each of them backs off if you define a bean of the
same type yourself (`@ConditionalOnMissingBean`), except the Micrometer metrics bean, which is created whenever a `MeterRegistry`
bean is present (define your own `ProcessEngineWorkerMetrics` bean and exclude the auto-configured one if needed):

| Bean type                    | Default implementation                                                      |
|------------------------------|-----------------------------------------------------------------------------|
| `VariableConverter`          | `JacksonVariableConverter` using the application's `ObjectMapper`           |
| `ParameterResolver`          | `ParameterResolver.builder().build()` with the default strategies           |
| `ResultResolver`             | `ResultResolver.builder().build()` with the default strategies              |
| `ProcessEngineWorkerMetrics` | `ProcessEngineWorkerMetricsMicrometer` if a `MeterRegistry` bean is present, `ProcessEngineWorkerMetricsNoOp` otherwise |
| `IdempotencyRegistry`        | `NoOpIdempotencyRegistry`, see [Idempotency Registry](./idempotency.md)     |

The registrar itself is configured by `ProcessEngineStarterRegistrar` and can be switched off by the property
`dev.bpm-crafters.process-api.worker.enabled=false`.

## Configuration

Add the properties to your `application.yml` (or `application.properties`):

```yaml
dev:
  bpm-crafters:
    process-api:
      worker:
        enabled: true # Enable or disable the worker registration
        complete-tasks-before-commit: false # Determines whether tasks are completed before transaction commit
        remove-task-result-on-completion: true # Remove the idempotency result after a successful completion
        tenant-id: my-tenant # Tenant id for all workers, can be overridden in the `@ProcessEngineWorker` annotation
```

The properties are bound to `ProcessEngineWorkerProperties` (relaxed binding, so `completeTasksBeforeCommit` works as well).
The property `register-process-workers` is deprecated and not evaluated, use `enabled` instead.

## Transactions

Annotate the worker method or class with `@org.springframework.transaction.annotation.Transactional` or
`@jakarta.transaction.Transactional`. The starter executes the worker method and the task completion in one transaction
via a `TransactionTemplate` bound to the application's `PlatformTransactionManager`. Applications without a transaction
manager start normally, as long as no transactional worker is invoked.

## Customization

Replace any of the auto-configured beans by declaring a `@Bean` of the same type:

```kotlin
@Configuration
class MyConfig {

  @Bean
  fun myParameterResolver(): ParameterResolver {
    return ParameterResolver.builder().addStrategy(
      MyCustomParameterResolutionStrategy(),
    ).build()
  }
}
```

See [Customizations](./process-engine-worker.md#customizations) for the details.

## Further features

* [Process Deployment](./process-deployment.md) - automatic deployment of BPMN and DMN resources on startup.
* [Idempotency Registry](./idempotency.md) - in-memory and JPA-based registries (`process-engine-worker-spring-boot-idempotency-registry-jpa`).
