---
title: Quarkus
---

# Quarkus workers

The extension integrates Process Engine Worker with Quarkus 3.40 LTS (3.40.1.1),
CDI, Jackson and Narayana JTA without Spring. Library bytecode targets Java 17;
use JDK 17 or 21 for JVM validation. Native acceptance is a separate validation
step, not implied by a successful JVM build.

## Dependencies and engine APIs

```xml
<dependency>
  <groupId>dev.bpm-crafters.process-engine-worker</groupId>
  <artifactId>process-engine-worker-quarkus</artifactId>
  <version>${process-engine-worker.version}</version>
</dependency>
```

The application supplies CDI beans for `TaskSubscriptionApi` and
`ServiceTaskCompletionApi` from Process Engine API. Install or implement an engine
adapter separately; the extension does not bundle one. Each API must resolve to a
unique default-qualified bean when worker registration is active. `DeploymentApi`
is optional and only needed for resource deployment.

## Worker beans

```kotlin
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class GreetingWorker {
  @ProcessEngineWorker(topic = "greet")
  fun greet(@Variable(name = "name") name: String): Map<String, Any?> =
    mapOf("greeting" to "Hello $name")
}
```

Use plain `@ApplicationScoped` or `@Singleton` CDI beans. Discovery uses CDI bean
identity, so application-qualified worker beans also work. Use the core annotations;
no Quarkus-specific worker annotation is required. The extension invokes the managed
bean and retains user interceptors, including inherited worker metadata.
It activates a request context during delivery when needed. Engine adapters must
deliver tasks on a **blocking thread**, not an event-loop thread. Ordinary application
calls to bean methods do not become task deliveries.

The default variable converter uses Quarkus's Jackson `ObjectMapper`, including
Kotlin support. Application CDI beans can replace `VariableConverter`,
`ParameterResolver`, `ResultResolver`, `IdempotencyRegistry` and
`ProcessEngineWorkerMetrics`. Supply one unambiguous default-qualified bean of each
customized type. A Micrometer registry enables the existing counters
`bpmcrafters.worker.external.tasks.{received,completed,failed,bpmn.error}` with
the `topic` tag; otherwise metrics default to no-op.

Shutdown unsubscribes registered workers and cleans up extension-owned resources.
The application remains responsible for the lifecycle of its engine adapter.

## Configuration

All settings below use the prefix `dev.bpm-crafters.process-api.worker`.

| Suffix | Default | Purpose |
| --- | --- | --- |
| `enabled` | `true` | Enable the worker runtime. |
| `register-process-workers` | `true` | Register discovered CDI worker methods. |
| `complete-tasks-before-commit` | `false` | Default automatic completion timing for transactional workers. |
| `remove-task-result-on-completion` | `true` | Remove cached results after successful automatic completion. |
| `tenant-id` | unset | Default tenant; a worker annotation can override it. |
| `deployment.enabled` | `false` | Deploy packaged resources at startup, independently of worker flags. |
| `deployment.deployment-timeout-in-seconds` | `30` | Maximum deployment wait time. |
| `deployment.bpmn-resource-pattern` | `classpath*:/**/*.bpmn` | Build-time-fixed BPMN selection. |
| `deployment.dmn-resource-pattern` | `classpath*:/**/*.dmn` | Build-time-fixed DMN selection. |

Only `classpath:` and `classpath*:` resource globs are supported. Filesystem and remote
schemes are rejected. Matching application and dependency-JAR resources are packaged
for JVM and native execution. Rebuild when changing the resource patterns; deployment
enablement and timeout remain runtime settings. Missing `DeploymentApi` skips startup
deployment. For manual deployment, inject
`dev.bpmcrafters.processengine.worker.quarkus.deploymentresource.ProcessDeployment`
and call `deployResources()`. See [Process Deployment](process-deployment.md).

## Transactions and completion

Use Jakarta `@Transactional` metadata on the worker method, class or CDI stereotype;
method metadata overrides class metadata. `REQUIRED`, `SUPPORTS` and `MANDATORY`
use starter-style owned transactions when delivery has no ambient transaction.
With an ambient transaction, the caller remains its owner. `REQUIRES_NEW` uses
the actual CDI transaction, suspending the caller's transaction as appropriate.

Result conversion, cache writes and `BEFORE_COMMIT` completion run inside the
actual worker transaction. For `REQUIRES_NEW`, parameter conversion occurs before
entering the typed CDI proxy and therefore outside the new worker transaction.
`AFTER_COMMIT` completion waits for an owned transaction
to commit, or defers until a joined ambient transaction commits. Rollback suppresses
after-commit completion. Jakarta `rollbackOn` and `dontRollbackOn` rules retain CDI
semantics. For `REQUIRES_NEW`, cache lookup/replay uses a separate short transaction;
a cache miss finishes that transaction before business execution begins.

The annotation's `completion` setting overrides the global default. With
`autoComplete = false`, the worker owns completion. A remote engine call is not
automatically an XA participant: neither completion timing guarantees exactly-once
processing across an engine and a database.

User interceptors remain active. An interceptor outside the JTA transaction boundary
can mutate a result or throw after commit; it cannot roll back work that has already
committed. Choose interceptor ordering with this boundary in mind.

## Idempotency

The default registry is no-op. For persistent
storage, add the optional `process-engine-worker-quarkus-idempotency-registry-jpa`
artifact with the same version. An application registry overrides JPA, which overrides
no-op. The application supplies a JDBC extension, the default persistence unit's
datasource configuration and schema migrations.

See [Idempotency Registry](idempotency.md#quarkus-registries) and the
[Quarkus JPA guide](../quarkus-idempotency-registry-jpa/README.md) for transaction-aware
storage, the compatible schema and native serialization registration.

## Example

See the [runnable example](../examples/quarkus-order-fullfillment/README.md) for Java order
fulfillment against Camunda 8.8.2 and separate JVM/native validation instructions. The
[packaged acceptance application](../itest/quarkus-extension-integration-test/README.md)
documents PostgreSQL prerequisites, runtime profiles and test coverage boundaries.
