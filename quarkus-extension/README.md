# Quarkus worker extension

This extension integrates the framework-neutral worker core with Quarkus 3.40 LTS
(3.40.1.1), CDI and Narayana JTA. Library bytecode targets Java 17.
See the [Quarkus guide](../docs/quarkus.md) for dependencies, configuration and
resource deployment, and the [runnable example](../examples/quarkus-worker/README.md)
for Kotlin order fulfillment against a real Camunda 8.8.2 Docker Compose stack.

## Runtime contract

- Add `process-engine-worker-quarkus` to the application, not its deployment artifact.
- Declare public instance worker methods on `@ApplicationScoped` or `@Singleton`
  CDI beans. The extension retains otherwise-unused workers and invokes CDI references,
  including interceptors. It preserves inherited and generic interface worker metadata.
  Override generic base methods with concrete signatures: core's reflection contract
  cannot resolve a type variable on an inherited, non-overridden method. The extension
  rejects such erased signatures instead of converting payloads to `Object`.
- Supply exactly one default-qualified CDI bean for each of `TaskSubscriptionApi`
  and `ServiceTaskCompletionApi`. The extension supplies no engine-specific adapter.
- Deliver tasks on blocking-capable threads. The core waits for engine API futures;
  the extension rejects event-loop delivery and activates a request context when needed.
- Shutdown drains active deliveries and unsubscribes acquired handles. It destroys
  owned dependent references, but never closes shared application engine clients.

## Configuration

Use the prefix `dev.bpm-crafters.process-api.worker`:

| Property | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Enable the runtime |
| `register-process-workers` | `true` | Subscribe discovered workers |
| `complete-tasks-before-commit` | `false` | Default automatic completion timing |
| `remove-task-result-on-completion` | `true` | Remove results after successful automatic completion |
| `tenant-id` | unset | Default tenant restriction |

Either disabled switch, or an application without workers, avoids resolving engine
API beans. Application CDI replacements override the default converter, parameter
resolver, result resolver, metrics, and no-op idempotency registry. The converter uses
Quarkus's Jackson mapper, with Java time, JDK8, and Kotlin modules. Metrics use the
core meter names and `topic` tag when the Quarkus Micrometer extension is present;
otherwise they perform no operations.

## Transaction coordination

The extension preserves ordinary CDI transactional interception. It does not rewrite
transaction annotations or invoke raw bean targets.

| Transaction type | Delivery behavior |
| --- | --- |
| `REQUIRED`, `SUPPORTS`, `MANDATORY` | Join an ambient transaction; otherwise establish an owned delivery envelope |
| `REQUIRES_NEW` | Finish a short cache lookup/replay transaction before invoking the actual CDI-owned worker transaction on a miss |
| `NOT_SUPPORTED` | Suspend the caller's transaction for the delivery and resume it afterwards |
| `NEVER` | Reject delivery in an ambient transaction without marking or ending that transaction |
| No transactional binding | Do not create a transaction; respect an existing transaction if one is present |

The extension records Arc's effective transactional binding, including stereotypes,
method overrides, `rollbackOn`, and `dontRollbackOn`. Business exceptions retain their
Jakarta rollback policy. Result-processing and completion infrastructure failures cross
the CDI boundary as runtime failures; core error reporting unwraps their original cause.

A delivery-scoped interceptor runs immediately inside Jakarta's transaction interceptor.
Core result conversion, cache registration, and automatic `BEFORE_COMMIT` completion run
there, including for `REQUIRES_NEW`. The extension never adds an independent outer write
transaction around a CDI `REQUIRES_NEW` call. Parameter conversion occurs before entering
the typed CDI proxy and therefore outside the new `REQUIRES_NEW` worker transaction.
A cache hit bypasses business invocation and
argument resolution; the coordinator still applies the appropriate transaction/completion
boundary. Concurrent misses may both execute, and a miss may become stale before invocation.
This remains a retry-result cache, not a duplicate-dispatch lock.

Core observes the result after all interceptors *inside* the delivery boundary. It stores
a top-level payload snapshot for caching/completion. Outer user interceptors still execute:
they may replace or mutate the returned map or throw after a CDI-owned commit. Such changes
do not replace the snapshot, and later failures are reported without claiming rollback of
already-committed business work. Payload values retain the core cache's existing shallow-copy
semantics. Ordinary bean calls never enter worker coordination and retain ordinary CDI behavior;
in particular, ordinary `SUPPORTS` does not create a transaction and ordinary `MANDATORY`
requires a caller-owned one.

An interceptor that supplies a result without proceeding must run *inside* the transactional
delivery boundary to supply a worker result. If an outer interceptor short-circuits before that
boundary, delivery fails clearly rather than treating its return value as a transaction-bound
worker result. Recovering an exception outside that boundary does not replace the failed inner
worker outcome. These constraints do not apply to ordinary bean calls.

Automatic `AFTER_COMMIT` completion runs only after successful commit. For a caller-owned
transaction, delivery returns without waiting for that caller to commit. A synchronization
dispatches completion/failure reporting to Quarkus's executor with a request context and no
ambient transaction. Engine futures may block that worker thread, never the event-loop or
caller-owned commit thread. If the managed executor rejects dispatch, a retained JDK common-pool
fallback reports the rejection with the same request-context/resource ownership.

Shutdown rejects new deliveries, drains accepted work, and awaits asynchronous unsubscriptions,
including late subscription handles. It cancels completion callbacks for uncommitted caller-owned
transactions instead of waiting for their commits. Those tasks remain available for engine
redelivery. Owned dependent references remain retained until the corresponding transaction
synchronizations finish, even though shutdown does not wait for that external outcome. Shutdown
from a delivery never waits for itself. Shared engine clients are never closed by the extension.
Engine subscription/unsubscription futures must eventually settle; the extension cannot cancel
an unknown engine subscription handle safely.

Manual completion stays under worker control. The core does not automatically remove
cached results for manual completion.

## Optional in-memory retry cache

The default registry is a no-op. To opt in, produce a CDI `IdempotencyRegistry`
using `JtaInMemoryIdempotencyRegistry(transactionSynchronizationRegistry)`.
This registry delegates storage to core's local registry, stages writes and removals
in transaction order, and publishes only on commit. It caches retry results; it
does **not** provide durable storage or atomic suppression of concurrent duplicates.

For durable storage, use the optional [Quarkus JPA registry](../quarkus-idempotency-registry-jpa/README.md).
It documents the default persistence unit, schema and Java serialization requirements.

## Native images and validation

The extension emits reflection metadata for worker methods, inherited annotations,
custom converters, and indexed payload type hierarchies. This metadata does not
replace native testing with the application's real payloads and engine adapter.
Register custom serialized payload classes as described in the JPA registry guide.

Use the [packaged acceptance application](../itest/quarkus-extension-integration-test/README.md)
to run JVM and native validation against PostgreSQL. The supported native builder
baseline is Mandrel/GraalVM 25.x. A successful JVM build does not establish native
compatibility.
