---
title: Process Engine Worker with Quarkus
---

If you build your process application with Quarkus, use the Process Engine Worker Quarkus extension. It wires the framework-free
`process-engine-worker-core` into CDI and discovers the worker methods at build time, analogous to what the Spring Boot starter
does for Spring. The extension is in `preview` status.

## Installation

Add the extension and a Quarkus process engine adapter of Process Engine API to your project's classpath:

```xml
<dependencies>
  <dependency>
    <groupId>dev.bpm-crafters.process-engine-worker</groupId>
    <artifactId>process-engine-worker-quarkus</artifactId>
    <version>${process-engine-worker.version}</version>
  </dependency>

  <!-- A Quarkus process engine adapter providing TaskSubscriptionApi and ServiceTaskCompletionApi, e.g. Camunda 8 -->
  <dependency>
    <groupId>dev.bpm-crafters.process-engine-adapters</groupId>
    <artifactId>process-engine-adapter-camunda-platform-c8-quarkus</artifactId>
  </dependency>
</dependencies>
```

The extension consists of a runtime artifact (`process-engine-worker-quarkus`) and a deployment artifact
(`process-engine-worker-quarkus-deployment`), which is resolved by the Quarkus build automatically. It requires Quarkus 3.x
and brings `quarkus-arc` and `quarkus-jackson` (for the default `VariableConverter`).

Now write a worker:

```java
public class MySmartWorker {

  private final FetchGoodsInPort fetchGoodsInPort;

  public MySmartWorker(FetchGoodsInPort fetchGoodsInPort) {
    this.fetchGoodsInPort = fetchGoodsInPort;
  }

  @ProcessEngineWorker(topic = "fetchGoods")
  public Map<String, Object> fetchGoods(
    @Variable(name = "order") Order order
  ) {
    var fetched = fetchGoodsInPort.fetchGoods(order);
    return Map.of("shipped", fetched);
  }
}
```

The annotations, parameter resolution, return types and error handling are described on the
[Process Engine Worker](./process-engine-worker.md) page.

## Build-time discovery

The deployment module scans the Jandex index of your application for methods annotated with `@ProcessEngineWorker`:

* Every class declaring a worker method automatically becomes a bean with `@Singleton` scope, if it has no scope annotation.
  You can still use `@Singleton` or `@ApplicationScoped` explicitly.
* Worker beans are marked unremovable, so `quarkus.arc.remove-unused-beans` never removes them, even if nothing injects them.
* The worker classes are registered at runtime init, before any `StartupEvent` observer runs (see below).
* Worker classes are registered for reflection, so native images work without further configuration.

Invalid worker definitions fail the build with a `ProcessEngineWorkerValidationException`. Validated are:

* worker methods must be `public` and must not be `static` or `abstract` (annotate the implementing method, not an abstract one),
* a worker method declared on an abstract class or interface is registered for every concrete subclass / implementation; every
  bean class carrying a worker must be a discovered bean (no `@Vetoed`),
* worker classes must be class beans: exposing a worker via a CDI producer (`@Produces`) is not supported and fails the build,
* the effective topic of each worker must be unique within the application (per tenant, if the `tenantId` attribute is used),
* Kotlin worker classes must have exactly one constructor usable by CDI: a Kotlin constructor with default arguments generates a
  second, no-arg constructor which Arc would pick silently. Either avoid default arguments in the constructor of a worker class
  or annotate the constructor to use with `@Inject`.

Only dependencies that are indexed are scanned. Your application classes are indexed automatically. If your workers live in a
library jar, add a Jandex index to it (`io.smallrye:jandex-maven-plugin`) or list it via
`quarkus.index-dependency.<name>.group-id` / `artifact-id` in `application.properties`.

## Provided beans

The extension registers the following beans. All of them are `@DefaultBean`s and are replaced by a user-provided producer or bean
of the same type:

| Bean type                    | Default implementation                                                     |
|------------------------------|----------------------------------------------------------------------------|
| `VariableConverter`          | `JacksonVariableConverter` using the Quarkus `ObjectMapper`                |
| `ParameterResolver`          | `ParameterResolver.builder().build()` with the default strategies          |
| `ResultResolver`             | `ResultResolver.builder().build()` with the default strategies             |
| `IdempotencyRegistry`        | `NoOpIdempotencyRegistry` (`EntityManagerJpaIdempotencyRegistry` if `process-engine-worker-idempotency-registry-jpa` and `quarkus-hibernate-orm` are present, see [Idempotency](./idempotency.md#quarkus)) |
| `ProcessEngineWorkerMetrics` | `ProcessEngineWorkerMetricsNoOp` (Micrometer implementation if `quarkus-micrometer` is present) |
| `TransactionalExecutor`      | `TransactionalExecutor.NONE` (JTA implementation if `quarkus-narayana-jta` is present) |

To customize a component, write a CDI producer:

```java
@Singleton
public class WorkerConfiguration {

  @Produces
  @ApplicationScoped
  public ParameterResolver parameterResolver() {
    return ParameterResolver.builder()
      .addStrategy(new MyCustomParameterResolutionStrategy())
      .build();
  }
}
```

The `TaskSubscriptionApi` and `ServiceTaskCompletionApi` are not provided by the worker extension, but by the process engine
adapter. If no `TaskSubscriptionApi` bean is available on startup, the worker registration fails with an `IllegalStateException`
pointing to the missing adapter.

## Configuration

The configuration keys are the same as for the Spring Boot starter and are placed in `application.properties`:

```properties
dev.bpm-crafters.process-api.worker.enabled=true
dev.bpm-crafters.process-api.worker.complete-tasks-before-commit=false
dev.bpm-crafters.process-api.worker.remove-task-result-on-completion=true
dev.bpm-crafters.process-api.worker.tenant-id=my-tenant
```

| Property                                                            | Default | Description                                                                                                                  |
|---------------------------------------------------------------------|---------|------------------------------------------------------------------------------------------------------------------------------|
| `dev.bpm-crafters.process-api.worker.enabled`                       | `true`  | Switches the worker registration on or off. The beans are still built, but no subscription is created.                       |
| `dev.bpm-crafters.process-api.worker.complete-tasks-before-commit`  | `false` | Determines whether tasks of transactional workers are completed before the transaction commit.                               |
| `dev.bpm-crafters.process-api.worker.remove-task-result-on-completion` | `true` | Removes the result from the [idempotency registry](./idempotency.md) after a successful completion.                        |
| `dev.bpm-crafters.process-api.worker.tenant-id`                     | none    | Tenant id for all workers, can be overridden by the `tenantId` attribute of the annotation.                                  |

The properties are runtime properties and can be changed without rebuilding the application. In tests, they can be overridden
via `@TestProfile` or `QuarkusExtensionTest.overrideConfigKey`.

## Transactions

Add the `quarkus-narayana-jta` extension to your project and annotate the worker method or the worker class with
`jakarta.transaction.Transactional`:

```xml
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-narayana-jta</artifactId>
</dependency>
```

```java
public class MyTransactionalWorker {

  @Inject
  OrderRepository orderRepository;

  @ProcessEngineWorker(topic = "storeOrder")
  @Transactional
  public void storeOrder(@Variable(name = "order") Order order) {
    orderRepository.persist(order);
  }
}
```

The annotation is a marker evaluated by the worker framework: the worker method and the completion of the task are executed
inside one JTA transaction (`REQUIRED` semantics via `QuarkusTransaction`). A failing completion (e.g. network error) rolls the
transaction back, a `BpmnErrorOccurred` thrown by the worker rolls back as well and reports the BPMN error to the engine. The
`completion` attribute of the annotation and the `complete-tasks-before-commit` property control, whether the task is completed
before or after the commit.

If `quarkus-narayana-jta` is not on the classpath, `@Transactional` workers are executed without transaction management and a
warning is logged at startup.

Kotlin workers: the worker framework's transaction handling does not depend on CDI interception. If you additionally rely on
the CDI `@Transactional` interceptor for other methods of the worker class, the class must be interceptable. Quarkus (ArC) removes
the `final` modifier of such classes at build time by default (`quarkus.arc.transform-unproxyable-classes=true`), so no further
configuration is required unless you disabled that option. If you prefer `kotlin-maven-allopen` instead, note that it only opens
classes carrying one of the configured annotations in source, so annotate the worker class explicitly with `@Singleton` or
`@ApplicationScoped`.

## Metrics

Add `quarkus-micrometer` (and a registry such as `quarkus-micrometer-registry-prometheus`). The extension then replaces the
no-op metrics with the Micrometer implementation (`ProcessEngineWorkerMetricsMicrometer`) using the Quarkus `MeterRegistry`.

## Idempotency

### In-memory registry

Provide an `InMemoryIdempotencyRegistry` via a producer. The extension installs the JTA after-commit hook on the registry
automatically, so results of transactional workers are only registered after a successful commit:

```java
@Singleton
public class IdempotencyConfiguration {

  @Produces
  @ApplicationScoped
  public IdempotencyRegistry idempotencyRegistry() {
    return new InMemoryIdempotencyRegistry();
  }
}
```

### JPA registry

Add `process-engine-worker-idempotency-registry-jpa` together with `quarkus-hibernate-orm` (which requires `quarkus-narayana-jta`)
and a JDBC driver. The extension then produces an `EntityManagerJpaIdempotencyRegistry` bound to the default persistence unit,
registers the `TaskLogEntry` entity and uses the same `task_log_entry_` table as the Spring Boot module.
See [Idempotency Registry](./idempotency.md#quarkus) for the details and the schema.

## Native images

* Worker classes and their methods are registered for reflection by the extension, as well as the parameter types of
  `@Variable` parameters, custom converter classes (`@Variable(converter = ...)`) and payload-compatible return types.
* Types nested in your variables that are not reachable through the worker signature (e.g. polymorphic payloads) must be
  registered by the application with `@RegisterForReflection`.
* The JPA idempotency registry uses Java serialization by default. For native images switch to the Jackson-based serializer,
  see [Idempotency Registry](./idempotency.md#quarkus).

Native mode is not part of this project's verification yet.

## Testing

Workers can be tested with `@QuarkusTest` without a running process engine by providing an in-memory `TaskSubscriptionApi`
and a recording `ServiceTaskCompletionApi`. `process-engine-api-impl` provides `AbstractTaskSubscriptionApiImpl`, which keeps
the subscriptions in a `SubscriptionRepository`, so a test can deliver tasks to the subscribed workers directly:

```java
public class InMemoryTaskSubscriptionApi extends AbstractTaskSubscriptionApiImpl {

  private final SubscriptionRepository repository;

  public InMemoryTaskSubscriptionApi() {
    this(new InMemSubscriptionRepository());
  }

  private InMemoryTaskSubscriptionApi(SubscriptionRepository repository) {
    super(repository);
    this.repository = repository;
  }

  @Override
  public MetaInfo meta(MetaInfoAware instance) {
    return new MetaInfo() {
    };
  }

  public void deliver(String topic, TaskInformation taskInformation, Map<String, Object> payload) {
    repository.getTaskSubscriptions().stream()
      .filter(subscription -> topic.equals(subscription.getTaskDescriptionKey()))
      .findFirst()
      .orElseThrow()
      .getAction()
      .accept(taskInformation, payload);
  }
}
```

Expose both APIs with a test-scoped producer (e.g. in `src/test/java`, annotated with `@Produces @ApplicationScoped`) or use
`@InjectMock` / `@Alternative` to replace the adapter beans. Then inject the in-memory API into the test and deliver a task:

```java
@QuarkusTest
class MySmartWorkerTest {

  @Inject
  InMemoryTaskSubscriptionApi subscriptionApi;

  @Inject
  RecordingServiceTaskCompletionApi completionApi;

  @Test
  void completesTask() {
    subscriptionApi.deliver("fetchGoods", taskInformation("task-1"), Map.of("order", new Order("4711")));

    assertThat(completionApi.completedTasks()).containsKey("task-1");
  }
}
```

To switch the worker registration off in a test, set `dev.bpm-crafters.process-api.worker.enabled=false` in the test profile.

## Startup ordering with the process engine adapter

The extension registers the workers during runtime initialization of the application, which happens before any `StartupEvent`
observer is notified. The Camunda 8 Quarkus adapter subscribes its job workers in a `StartupEvent` observer and snapshots the
subscription repository at that point, so all `@ProcessEngineWorker` methods are guaranteed to be subscribed in time. Workers
are unsubscribed on `ShutdownEvent` before the adapter closes its deliveries.

Remember that the adapter must be enabled explicitly, otherwise its `TaskSubscriptionApi` fails on first use:

```properties
dev.bpm-crafters.process-api.adapter.c8.enabled=true
dev.bpm-crafters.process-api.adapter.c8.service-tasks.delivery-strategy=SUBSCRIPTION
dev.bpm-crafters.process-api.adapter.c8.service-tasks.worker-id=my-worker
dev.bpm-crafters.process-api.adapter.c8.user-tasks.delivery-strategy=SCHEDULED
```

See the Camunda 8 Quarkus quickstart of Process Engine API for the adapter configuration.

## Limitations

* Automatic deployment of BPMN / DMN resources is currently supported for Spring Boot only, see [Process Deployment](./process-deployment.md).
* Reactive or asynchronous return types of worker methods are not supported.
* An example application for Quarkus follows as soon as the Camunda 8 Quarkus adapter of Process Engine API is released.
