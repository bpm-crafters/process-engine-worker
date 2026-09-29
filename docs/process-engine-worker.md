---
title: Process Engine Worker
---

Process Engine Worker is an independent component built on top of Process Engine API in order to accelerate the development of agnostic workers
for any process engine supported by Process Engine API. By doing so, it abstracts from specific worker clients and
API and allows to build universal workers.

The worker logic itself is framework-agnostic (`process-engine-worker-core`) and is integrated into your application
framework by a dedicated module:

| Framework           | Artifact                                    | Details                             |
|---------------------|---------------------------------------------|-------------------------------------|
| Spring Boot         | `process-engine-worker-spring-boot-starter` | [Spring Boot page](./spring-boot.md) |
| Quarkus             | `process-engine-worker-quarkus`             | [Quarkus page](./quarkus.md)         |
| Plain Java / Kotlin | `process-engine-worker-core`                | Use `ProcessEngineWorkerRegistrar`  |

The annotations, parameter resolution, result handling, error handling and configuration keys described on this page are
the same for all frameworks. Framework specific details are described on the framework pages.

## Dependencies

### Spring Boot

Add the Process Engine Worker starter to your projects classpath. In Maven add the following to your `pom.xml`:

```xml
<dependency>
  <groupId>dev.bpm-crafters.process-engine-worker</groupId>
  <artifactId>process-engine-worker-spring-boot-starter</artifactId>
  <version>${process-engine-worker.version}</version>
</dependency>
```

Now create a simple Spring component and annotate a method with a special annotation `@ProcessEngineWorker`:

```java

@Component
@RequiredArgsConstructor
public class MySmartWorker {

  private final FetchGoodsInPort fetchGoodsInPort;

  @ProcessEngineWorker(topic = "fetchGoods")
  public Map<String, Object> fetchGoods(
    @Variable(name = "order") Order order
  ) {
    // execute some business code
    var fetched = fetchGoodsInPort.fetchGoods(order);

    return Map.of("shipped", fetched);
  }
}
```

### Quarkus

Add the Process Engine Worker Quarkus extension to your projects classpath. In Maven add the following to your `pom.xml`:

```xml
<dependency>
  <groupId>dev.bpm-crafters.process-engine-worker</groupId>
  <artifactId>process-engine-worker-quarkus</artifactId>
  <version>${process-engine-worker.version}</version>
</dependency>
```

Now create a class and annotate a method with `@ProcessEngineWorker`. No scope annotation is required, the extension
turns every class with a worker method into a `@Singleton` bean at build time:

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
    // execute some business code
    var fetched = fetchGoodsInPort.fetchGoods(order);

    return Map.of("shipped", fetched);
  }
}
```

## Worker annotation

The `@ProcessEngineWorker` annotation supports the following properties:

| Property       | Type      | Default     | Description                                                                                                                                                                                                    |
|----------------|-----------|-------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `topic`        | `String`  | `"__unset"` | Topic name to subscribe this worker for. Alias for `value`.                                                                                                                                                    |
| `value`        | `String`  | `"__unset"` | Topic name to subscribe this worker for. Alias for `topic`.                                                                                                                                                    |
| `autoComplete` | `boolean` | `true`      | Flag indicating if the task should be automatically completed after the worker execution. If the return type is `Map<String, Any>`, it will overrule this setting and auto-complete with the returned payload. |
| `completion`   | `enum`    | `DEFAULT`   | Configures when the worker completes a task if `autoComplete` is active. Possible values are `DEFAULT`, `BEFORE_COMMIT`, and `AFTER_COMMIT`. Has no effect if the worker is not transactional.                 |
| `lockDuration` | `long`    | `-1`        | Optional lock duration in milliseconds for this worker. If set to `-1` (default), the global configuration of the process engine adapter will be used. (Available since `0.8.0`)                               |
| `tenantId`     | `String`  | `""`        | Optional tenant id to be used for current worker during the registration. Any non-blank value will be considered. If the value is empty, the value from the property will be used.                             |

`topic` and `value` are aliases of each other: `@ProcessEngineWorker("fetchGoods")` and `@ProcessEngineWorker(topic = "fetchGoods")`
are equivalent. Specify either of them, but never both with different values: the registration fails with an
`IllegalStateException` in that case. The same rule applies to `name` and `value` of `@Variable`.

## Method parameter resolution

Parameter resolution of the method annotated with `ProcessEngineWorker` is based on a set of strategies
registered by the `ParameterResolver` bean. Currently, the following parameters are resolved:

| Type                                  | Purpose                                                                  |
|---------------------------------------|--------------------------------------------------------------------------|
| TaskInformation                       | Helper abstracting all information about the external task.              |
| ServiceTaskCompletionApi              | API for completing the external task manually                            |
| VariableConverter                     | Special utility to read the process variable map and deliver typed value | 
| Map<String, Object>                   | Payload object containing all variables.                                 |
| Type annotated with @Variable("name") | Marker for a process variable.                                           |

Usually, the requested variable is mandatory and the parameter resolver reports an error, if the requested variable is not
available in the process payload. If you want to inject the variable only if it exists in the payload you have two options.
Either you set the parameter `@Variable(name = "...", mandatory = false)` or you use `Optional<T>` instead of `T` as a variable
type. If you are using Kotlin and don't like `Optional`, make sure to declare variable type as nullable (`T?` instead of `T`) and
set the mandatory flag to `false`.

A variable can be converted by a dedicated converter using `@Variable(name = "...", converter = MyConverter.class)`. The converter
class must implement `VariableConverter` and provide a public no-arg constructor (or be a Kotlin `object`), since it is
instantiated reflectively by the library. If no converter is specified, the globally configured `VariableConverter` bean is used.

## Method return type

If the return type of the method is of type `Map<String, Object>` or compatible and the `autoComplete` flag is turned
on the annotation (defaults to `true`), the library will try to automatically complete the External Task
using the returned map as completion variables. If `autoComplete` is `true`, but no return value is provided, the task
will be completed without empty payload. This functionality is provided by the `ResultResolver` based on registered strategies.

Reactive or asynchronous return types (`CompletionStage`, `Uni`, `Mono`, ...) are not supported: the worker method is
executed synchronously and the task is completed when the method returns.

## Throwing a BPMN Error

If you want to throw a BPMN error, please throw an instance of a `BpmnErrorOccurred` exception from the method body. The exception
is a checked exception, in order to comply with the behavior of Spring and Jakarta transactions of not rolling back a transaction on checked exceptions.
If the worker is transactional (see below), the library rolls back the transaction and reports the BPMN error to the engine
regardless of the framework.

## Failing a task and retries

Any other exception thrown by the worker method fails the task in the process engine and decrements the retry counter. To control the
retries and the backoff explicitly, throw a `FailJobException` providing the number of remaining retries and the retry timeout.

## Transactional support

The worker method can be marked transactional by adding a transactional annotation to the method or to the worker class.
If the worker is transactional, the library executes the worker method and the completion of the external task via API in
the same transaction. This will lead to a transaction rollback, if the external task can't be completed (e.g. due to a network error).
By default, the completion happens after the commit of the transaction; this can be changed globally by the
`complete-tasks-before-commit` property (`true` completes before the commit) or per worker via the `completion` attribute of the annotation.

The following annotations are detected:

* `jakarta.transaction.Transactional` with type `REQUIRED`, `REQUIRES_NEW`, `SUPPORTS` or `MANDATORY`.
* `org.springframework.transaction.annotation.Transactional` with propagation `REQUIRED`, `REQUIRES_NEW`, `SUPPORTS` or `MANDATORY` (detected by name, works without Spring on the classpath).

### Spring Boot

Transactions are managed by the `PlatformTransactionManager` of your application (via `TransactionTemplate`). Both the Spring and the
Jakarta annotation are supported. Since the worker invocation goes through the Spring proxy of your bean, Spring's own
`@Transactional` interception applies as well, but the worker framework already opens the transaction, so the interceptor just joins it.

### Quarkus

Transactions require the `quarkus-narayana-jta` extension on the classpath. Annotate the worker method or class with
`jakarta.transaction.Transactional`. The annotation is evaluated as a marker by the worker framework, which executes the
worker and the completion via `QuarkusTransaction` with `REQUIRED` semantics. If `quarkus-narayana-jta` is not present,
transactional workers are executed without a transaction and a warning is logged.

Note for Kotlin workers: the worker framework's own transaction handling does not depend on CDI interception. If you additionally
rely on the CDI `@Transactional` interceptor for other methods of a (final) Kotlin worker class, Quarkus makes the class interceptable
at build time by default (`quarkus.arc.transform-unproxyable-classes=true`); see the [Quarkus page](./quarkus.md#transactions).

More details can be found on the [Quarkus page](./quarkus.md).

## Configuration

The configuration keys are the same for all frameworks and live under the prefix `dev.bpm-crafters.process-api.worker`:

| Property                           | Type      | Default | Description                                                                                                                                            |
|------------------------------------|-----------|---------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| `enabled`                          | `boolean` | `true`  | Switches the worker registration on or off (e.g. in integration tests).                                                                                |
| `complete-tasks-before-commit`     | `boolean` | `false` | Determines whether tasks of transactional workers are completed before the transaction commit. Can be overridden per worker by `completion`.           |
| `remove-task-result-on-completion` | `boolean` | `true`  | Removes the result from the [idempotency registry](./idempotency.md) after the task has been completed successfully.                                   |
| `tenant-id`                        | `String`  | none    | Tenant id for all workers, can be overridden by the `tenantId` attribute of the `@ProcessEngineWorker` annotation.                                     |

### Spring Boot

Add the properties to your `application.yml`:

```yaml
dev:
  bpm-crafters:
    process-api:
      worker:
        enabled: true
        complete-tasks-before-commit: false
        remove-task-result-on-completion: true
        tenant-id: my-tenant
```

The property `register-process-workers` found in older versions of this documentation is deprecated and has no effect.
Use `enabled` instead.

### Quarkus

Add the properties to your `application.properties`:

```properties
dev.bpm-crafters.process-api.worker.enabled=true
dev.bpm-crafters.process-api.worker.complete-tasks-before-commit=false
dev.bpm-crafters.process-api.worker.remove-task-result-on-completion=true
dev.bpm-crafters.process-api.worker.tenant-id=my-tenant
```

## Customizations

The following components are provided with defaults by the framework modules and can be replaced by your own implementation:
`VariableConverter` (Jackson based), `ParameterResolver`, `ResultResolver`, `IdempotencyRegistry` (no-op),
`ProcessEngineWorkerMetrics` (Micrometer if available, no-op otherwise).

You might want to register your own parameter resolution strategies. For this purpose, please construct
the parameter resolver bean on your own and register your own strategies. Your custom strategy must implement
`ParameterResolutionStrategy` interface. Optionally, you might want to register own result resolution strategies.
For this purpose, please construct the result resolver bean on your own and register your own strategies.

### Spring Boot

Expose your component as a `@Bean`, the default bean of the auto-configuration backs off (`@ConditionalOnMissingBean`):

```kotlin

@Configuration
class MyConfig {

  @Bean
  fun myParameterResolver(): ParameterResolver {
    return ParameterResolver.builder().addStrategy(
      MyCustomParameterResolutionStrategy(),
    ).build()
  }

  @Bean
  fun myResultResolver(): ResultResolver {
    return ResultResolver.builder().addStrategy(
      ResultResolutionStrategy(
        resultMatcher = { method -> ... },
        resultConverter = { result -> ... },
      ),
    ).build()
  }
}

```

### Quarkus

Expose your component using a CDI producer. The extension's beans are `@DefaultBean`s and are replaced by your producer:

```java
@Singleton
public class MyConfig {

  @Produces
  @ApplicationScoped
  public ParameterResolver myParameterResolver() {
    return ParameterResolver.builder().addStrategy(
      new MyCustomParameterResolutionStrategy()
    ).build();
  }

  @Produces
  @ApplicationScoped
  public ResultResolver myResultResolver() {
    return ResultResolver.builder().addStrategy(
      new ResultResolutionStrategy(
        method -> ...,
        result -> ...
      )
    ).build();
  }
}
```

## Further reading

* [Process Engine Worker with Spring Boot](./spring-boot.md)
* [Process Engine Worker with Quarkus](./quarkus.md)
* [Idempotency Registry](./idempotency.md)
* [Process Deployment](./process-deployment.md)
