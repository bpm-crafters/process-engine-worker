# Packaged Quarkus worker acceptance application

This unpublished application checks the worker extension, optional JPA registry,
resource deployment, and Micrometer integration over HTTP. Failsafe launches the
packaged application in a separate process through `@QuarkusIntegrationTest`.
The same tests target a JVM fast JAR or a native executable. Tests do not inject
application CDI beans or replace application services with test-only mocks.

The application's CDI engine fixture implements the public subscription,
completion, and deployment APIs. It retains real subscription commands and
handles, delivers tasks through registered actions, records API effects, and
returns controllable immediate, failed, or pending futures. Blocking REST worker
threads deliver tasks. Condition-based waits observe pending futures; tests do
not use arbitrary sleeps.

The integration launcher requests `quarkus.http.test-port=0` and lets Quarkus
discover the assigned port and configure RestAssured. This isolates the harness
from parallel reactor applications without changing the normal runnable
application port. A black-box check compares the observed HTTP port and process
ID, and verifies the JVM/native runtime marker against the selected Maven
profile; Quarkus selects the executable using its generated artifact metadata.
Every harness HTTP client has bounded connection/read timeouts. Deferred-test
cleanup durably releases even an unregistered completion, joins its delivery
with a timeout, and preserves primary assertion failures through
Kotlin `use` suppression.

**Do not deploy this harness.** Its unauthenticated HTTP controls are test-only.
The module explicitly skips Maven deployment, signing, and generated publication
documentation. Its fixtures do not prove native compatibility of any real engine
adapter; each provider must validate its own client and transport dependencies.

## Prerequisites

- Run Maven from the repository root with a JDK supported by Quarkus 3.40.1.1.
  The inherited compiler configuration targets Java 17 bytecode.
- Provide Docker access. The external test-resource manager starts
  `postgres:17-alpine`, creates the application's two tables, and supplies JDBC
  credentials to the launched process. It opens independent JDBC connections
  from the test JVM for database assertions. PostgreSQL 17 is a supported,
  stable major version; the Alpine tag avoids a large test image.
- Disable no tests globally: do not pass `-DskipITests`, which removes the entire
  `itest` aggregator from the root reactor. `-DskipITs` skips Failsafe execution;
  it does not remove the module.
- For native acceptance, allow the configured Mandrel builder image to run:
  `quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25`.
  The supported native builder baseline is Mandrel/GraalVM 25.x. The image's
  floating `jdk-25` tag can advance; record the resolved digest/version in validation logs.
  Reserve sufficient memory/disk and do not use parallel `-T4` native builds.

## Focused commands

Run commands from the repository root. First install the deployment modules once
so Quarkus bootstrap can resolve extension metadata:

```shell
./mvnw -B -ntp -DskipExamples -DskipTests \
  -pl quarkus-extension/deployment,quarkus-idempotency-registry-jpa/deployment -am install
```

Packaged JVM acceptance:

```shell
./mvnw -B -ntp -DskipExamples -DskipITs=false \
  -pl itest/quarkus-extension-integration-test -am verify
```

Packaged native acceptance, using the container builder:

```shell
./mvnw -B -ntp -DskipExamples -Pnative -Dnative -DskipITs=false \
  -Dquarkus.native.container-build=true \
  -Dquarkus.native.builder-image=quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25 \
  -pl itest/quarkus-extension-integration-test -am verify
```

The `native` profile exists only in this application module. It disables JAR
packaging and selects native packaging; it does not turn extension or core
modules into native applications. The test-scoped deployment dependency keeps
both extension deployment modules in focused `-am` reactor selections without
shipping them as runtime application dependencies.

Run a second acceptance launch with successful-completion cache retention:

```shell
./mvnw -B -ntp -DskipExamples -DskipITs=false \
  -Dquarkus.test.integration-test-profile=retained -Dacceptance.retained=true \
  -pl itest/quarkus-extension-integration-test -am verify
```

Add `-Pnative -Dnative` and the container-builder options above to validate that
runtime profile against the native executable.
`acceptance.retained` changes only the external test expectations;
`quarkus.test.integration-test-profile=retained` selects the application's
`%retained` runtime configuration. Set both together. Default runs require
cleanup after successful automatic completion. Resource patterns remain fixed
at build time in either runtime profile.

After the native build above, run the retained profile against the same native
executable without rebuilding it:

```shell
./mvnw -B -ntp -DskipExamples -Pnative -Dnative -DskipITs=false \
  -Dquarkus.test.integration-test-profile=retained -Dacceptance.retained=true \
  -DfailsafeArgLine= -pl itest/quarkus-extension-integration-test \
  failsafe:integration-test failsafe:verify
```

The empty `failsafeArgLine` removes only the inherited test-JVM coverage-agent
argument. Direct Failsafe goals do not initialize the lifecycle's JaCoCo agent;
this setting does not skip tests. Use the lifecycle commands above when changing
application or extension code, since direct goals reuse the existing packaged application.
Reports appear under `target/failsafe-reports`; copy that directory before the
second launch to retain both profiles' results. See the
[example validation guide](../../examples/quarkus-order-fullfillment/README.md#jvm-and-native-validation)
for local JVM and native validation instructions.

## Coverage and boundaries

The black-box suite checks:

- Automatic CDI worker discovery, interface/generic annotation metadata,
  Serializable DTO/enum conversion, a custom variable converter, and manual
  completion without a second automatic completion.
- Explicit retry count/backoff, business error reporting, and actual Micrometer
  received/completed/error/failure counters.
- REQUIRED and REQUIRES_NEW workers in both completion modes. Completion failure
  before commit must roll back business and registry rows. Failure after commit
  must preserve both; redelivery must reuse the cached result without invoking
  business code again. Successful completion follows the selected retention
  policy. Repeated failed replays preserve the original cache bytes; successful
  replays in the retained profile also bypass business code. The deferred
  success/failure matrix checks database visibility before release and after
  commit or rollback in both completion modes.
- REQUIRED, MANDATORY, and SUPPORTS own transactions when no ambient transaction
  exists. With one, the suite compares actual transaction keys captured at the
  endpoint and worker, and verifies rollback undoes business/cache writes without
  successful AFTER_COMMIT completion. REQUIRES_NEW uses a different transaction
  and survives an outer rollback.
- Runtime failure after persistence, a real PostgreSQL CHECK-constraint flush
  failure after the worker returns, and a nested nonserializable worker result
  all roll back business/cache writes without automatic completion. Corrupt
  persisted serialization must fail explicitly, never masquerade as a cache miss.
- Missing row versus SQL NULL/empty map, registration updates, fresh-transaction
  reads, per-task removal, and per-process cleanup with unrelated rows retained.
- Both serialization directions through real PostgreSQL: the external JVM
  writes standard `ObjectOutputStream` bytes for the application to read; the
  application constructs Java/Kotlin containers internally, persists them, and
  the external JVM reads their bytes with `ObjectInputStream`. Samples include
  mutable/immutable/singleton/unmodifiable/empty containers, Kotlin built
  collections, nested DTOs, enums, nulls, numeric/date scalars, every primitive
  array kind, DTO arrays, multidimensional arrays, shared-reference identity,
  and serialization delegates. Checks use deep contents plus identity, not
  `Map.equals` for arrays. Custom DTOs and their array types explicitly register
  native serialization support. Common JDK array serialization relies on the
  extension's metadata and requires packaged native validation.
- Automatic startup and manual resource deployment. Maven copies the existing
  tracked Spring integration BPMN fixture without modifying it. The DMN resource
  slot uses a clearly labeled `.txt` fixture. Startup and manual deployment checks
  compare SHA-256 over the received raw bytes with independently read source
  fixtures, not substring markers or JSON-reencoded bytes. They do not claim
  BPMN/DMN validity or real-engine deployment.

This suite does not restart a single application against the same database, run
a real engine provider, exhaust every transaction propagation/failure
combination, or infer native registration for arbitrary application payloads.
Cross-process serialization checks use one launched application plus the
external JVM, not a JVM-to-native restart. Keep historical DTO classes and their
compatible `serialVersionUID` values available when reading existing databases.

Focused unit tests also cover durable release, cleanup suppression/join ordering
and deep serialization checks. Run both JVM and native acceptance: neither static
compilation nor JVM execution establishes native compatibility.

Bodyless HTTP controls do not require JSON media types; only delivery and registry
payload endpoints consume JSON. The 36-case black-box matrix includes non-JSON
rejection on those two endpoints. Requested ambient rollback handles only the
runner's specific rollback exception after the harness marks rollback-only;
ordinary transaction failures still propagate. Caller-owned AFTER_COMMIT work
runs asynchronously, so ambient assertions poll API effects and fresh database
visibility together, including cache cleanup. Rollback assertions wait for the
explicit failure event before checking that no completion succeeded.

See the [Quarkus guide](../../docs/quarkus.md) and
[JPA registry guide](../../quarkus-idempotency-registry-jpa/README.md) for the
application contracts exercised by this harness.
