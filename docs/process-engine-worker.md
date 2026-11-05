---
title: Process Engine Worker
---

Process Engine Worker is an independent component built on top of Process Engine API in order to accelerate the development of agnostic workers
for any process engine supported by Process Engine API. Its core runs without Spring; the Spring Boot starter adds framework integration. It abstracts from specific worker clients and
API and allows to build universal workers.

## Framework-neutral registration

Add the core artifact instead of the Spring Boot starter:

```xml
<dependency>
  <groupId>dev.bpm-crafters.process-engine-worker</groupId>
  <artifactId>process-engine-worker-core</artifactId>
  <version>${process-engine-worker.version}</version>
</dependency>
```

Provide Process Engine API subscription and completion clients and a variable converter. This Java example
uses only core and Process Engine API types:

```java
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker;
import dev.bpmcrafters.processengine.worker.Variable;
import dev.bpmcrafters.processengine.worker.registrar.ProcessEngineWorkerRegistrar;
import dev.bpmcrafters.processengine.worker.registrar.VariableConverter;
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi;
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi;
import java.util.Map;

public class PlainWorker {
  @ProcessEngineWorker("greet")
  public Map<String, Object> greet(@Variable("name") String name) {
    return Map.of("greeting", "Hello " + name);
  }

  public static void register(TaskSubscriptionApi subscriptions,
                              ServiceTaskCompletionApi completion,
                              VariableConverter converter) {
    var registrar = new ProcessEngineWorkerRegistrar(subscriptions, completion, converter);
    registrar.register(new PlainWorker());
  }
}
```

The three-argument constructor supplies default parameter/result resolvers, no-op metrics and no-op
idempotency storage. Plain-object registration discovers public worker methods, including inherited and
interface metadata, and does not infer transactions. Registration waits for each subscription to complete.
Compile with `-parameters` when you rely on parameter-name fallback rather than explicit variable names.

Java shorthand annotations set `value`; Kotlin positional annotations set `topic` or `name`.
The runtime resolves both aliases explicitly. Equal explicit aliases are valid; conflicting aliases fail
registration. Unset aliases fall back to the method or parameter name. Core does not implement Spring
meta-annotation synthesis.

For framework integration, use the full constructor with `WorkerRuntimeOptions`, API clients, converters,
resolvers, `WorkerTransactionExecutor`, metrics and an `IdempotencyRegistry`. Register each worker with
`register(concreteMethod, metadataMethod, transactional, arguments -> managedMethod.invoke(managedProxy, arguments))`.
Use the concrete target method for parameter and result types and the annotated interface method for inherited
annotations. The existing three-argument overload remains available and resolves inherited annotations
from its supplied method; pass a concrete method to retain its narrowed types. Plain-object registration
discovers both methods automatically. Resolve compiler bridges and register each
logical worker only once. For generic JDK proxies, invoke the exposed erased interface signature, not a
narrowed implementation signature that the proxy does not expose.
Keep annotation metadata separate from the invocable method so JDK and class proxies retain interception.
The transaction executor must run the supplied action atomically and return after commit, propagating
rollback and commit failures. `BEFORE_COMMIT` completes within that action; `AFTER_COMMIT` completes after
the executor returns. Non-transactional workers complete directly; `autoComplete = false` leaves completion
to the worker in every mode.

Framework adapters own managed discovery, lifecycle/subscription shutdown, transaction execution and
transaction-aware storage, configuration binding, and resource deployment. The Spring starter implements
Spring discovery, proxy invocation, transaction execution, properties and startup deployment; core contains
the shared subscription, argument/result resolution, invocation, retry/error handling and idempotency logic.
Core has no Spring dependencies. Jackson and Micrometer integrations are optional: add their libraries
when you use `JacksonVariableConverter` or Micrometer metrics. No Quarkus/CDI integration is included.

## Spring Boot registration

First of all add the Process Engine Worker dependency to your projects classpath. In Maven add the following to your `pom.xml`:

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

The `@ProcessEngineWorker` annotation supports the following properties:

| Property       | Type      | Default     | Description                                                                                                                                                                                                    |
|----------------|-----------|-------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `topic`        | `String`  | `"__unset"` | Topic name to subscribe this worker for. Alias for `value`.                                                                                                                                                    |
| `autoComplete` | `boolean` | `true`      | Flag indicating if the task should be automatically completed after the worker execution. If the return type is `Map<String, Any>`, it will overrule this setting and auto-complete with the returned payload. |
| `completion`   | `enum`    | `DEFAULT`   | Configures when the worker completes a task if `autoComplete` is active. Possible values are `DEFAULT`, `BEFORE_COMMIT`, and `AFTER_COMMIT`. Has no effect if the worker is not transactional.                 |
| `lockDuration` | `long`    | `-1`        | Optional lock duration in milliseconds for this worker. If set to `-1` (default), the global configuration of the process engine adapter will be used. (Available since `0.8.0`)                               |
| `tenantId`     | `String`  | `""`        | Optional tenant id to be used for current worker during the registration. Any non-blank value will be considered. If the value is empty, the value from the property will be used.                             |

## Method parameter resolution

Parameter resolution of the method annotated with `ProcessEngineWorker` is based on a set of strategies
registered by the `ParameterResolver` bean. Currently, the following parameters are resolved:

| Type                                  | Purpose                                                                  |
|---------------------------------------|--------------------------------------------------------------------------|
| TaskInformation                       | Helper abstracting all information about the external task.              |
| ExternTaskCompletionApi               | API for completing the external task manually                            |
| VariableConverter                     | Special utility to read the process variable map and deliver typed value | 
| Map<String, Object>                   | Payload object containing all variables.                                 |
| Type annotated with @Variable("name") | Marker for a process variable.                                           |

Usually, the requested variable is mandatory and the parameter resolver reports an error, if the requested variable is not
available in the process payload. If you want to inject the variable only if it exists in the payload you have two options.
Either you set the parameter `@Variable(name = "...", mandatory = false)` or you use `Optional<T>` instead of `T` as a variable
type. If you are using Kotlin and don't like `Optional`, make sure to declare variable type as nullable (`T?` instead of `T`) and
set the mandatory flag to `false`.

## Method return type

If the return type of the method is of type `Map<String, Object>` or compatible and the `autoComplete` flag is turned
on the annotation (defaults to `true`), the library will try to automatically complete the External Task
using the returned map as completion variables. If `autoComplete` is `true`, but no return value is provided, the task
will be completed without empty payload. This functionality is provided by the `ResultResolver` based on registered strategies.

## Throwing a BPMN Error

If you want to throw a BPMN error, please throw an instance of a `BPMNErrorOccured` exception from the method body. The exception
is a checked exception, in order to comply with Spring behavior of not rolling back transaction on checked exceptions.

## Transactional support

The worker method can be marked transactional by adding Spring or Jakarta EE transactional annotations to the method or to the worker class.
If the annotation `@org.springframework.transaction.annotation.Transactional` with propagation `REQUIRES`, `REQUIRES_NEW`, `SUPPORTS` and `MANDATORY`
is used, the library will execute the worker method and the completion of the external task via API in the same transaction. This will lead to
a transaction rollback, if the external task can't be completed (e.g. due to a network error).

## Configuration

You can configure the Process Engine Worker by adding the following properties to your `application.properties` or `application.yml`:

```yaml
dev:
  bpm-crafters:
    process-api:
      worker:
        registerProcessWorkers: true # Enable or disable automatic worker registration
        completeTasksBeforeCommit: false # Determines whether tasks are completed before transaction commit
        tenantId: my-tenant # Specify the tenant ID for all workers, can be overridden by the value in the `@ProcessEngineWorker` annotation
```



