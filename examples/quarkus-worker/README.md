# Quarkus worker demo

This self-contained application registers a CDI worker, converts a map into a typed
`GreetingRequest`, and automatically completes the task with the worker's result.
It uses Quarkus 3.40 LTS (3.40.1.1) and Java 17 or later. It does not use Spring.

`DemoEngineAdapter` implements `TaskSubscriptionApi` and `ServiceTaskCompletionApi`
in memory. **It is not a production process engine adapter.** It supports synchronous
delivery only and provides no persistence, retries, task locking or deployment API.
Replace it with an engine adapter that exposes these APIs as CDI beans in a real application.

## Run

From the repository root, install the extension and build the example. Select the
deployment module explicitly because Quarkus resolves it during augmentation:

```shell
./mvnw -B -ntp -pl quarkus-extension/deployment,examples/quarkus-worker -am install -DskipITests
java -jar examples/quarkus-worker/target/quarkus-app/quarkus-run.jar
```

Send a task through the demo endpoint:

```shell
curl --fail -H 'Content-Type: application/json' \
  -d '{"name":"Ada"}' http://localhost:8080/demo/greetings
# {"greeting":"Hello Ada"}
```

For development mode, run `../../mvnw quarkus:dev` from this directory after installation.
The build runs `GreetingResourceTest`, which checks CDI registration, typed variable
conversion, invocation and completion through the HTTP endpoint, plus input validation.
`DemoEngineAdapterTest` checks cleanup after failed, throwing and incomplete deliveries,
then verifies that a subsequent delivery succeeds without retaining pending state.

See the [Quarkus guide](../../docs/quarkus.md) for configuration, transactions,
resource deployment and optional persistent idempotency storage.

## JVM and native validation

The greeting demo above runs without an external engine or database. The following
commands instead validate the separate
[packaged acceptance application](../../itest/quarkus-extension-integration-test/README.md)
at `itest/quarkus-extension-integration-test`. That application uses PostgreSQL
Testcontainers and exercises worker, JPA registry and resource-deployment behavior.
Its README describes prerequisites, runtime profiles and coverage boundaries.
The `native` Maven profile belongs to that acceptance application, not this greeting demo.

Run commands from the repository root with JDK 17 or 21 and Docker available.
For normal JVM validation across the repository, run:

```shell
./mvnw verify
```

For focused acceptance, first install the deployment modules for Quarkus bootstrap
resolution. Runtime-to-deployment resolution is not a normal Maven dependency edge.
The acceptance application's test-scoped deployment dependency includes both deployment
modules in its focused `-am` selection without adding them to the runtime application.

```shell
./mvnw -B -ntp -DskipExamples -DskipTests \
  -pl quarkus-extension/deployment,quarkus-idempotency-registry-jpa/deployment -am install
./mvnw -B -ntp -DskipExamples -DskipITs=false \
  -pl itest/quarkus-extension-integration-test -am verify
```

Build the packaged native acceptance application and run Failsafe against it:

```shell
./mvnw -B -ntp -DskipExamples -Pnative -Dnative -DskipITs=false \
  -Dquarkus.native.container-build=true \
  -Dquarkus.native.builder-image=quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25 \
  -pl itest/quarkus-extension-integration-test \
  -am verify
```

Then run the retained-cache profile against the same native executable without
rebuilding it:

```shell
./mvnw -B -ntp -DskipExamples -Pnative -Dnative -DskipITs=false \
  -Dquarkus.test.integration-test-profile=retained -Dacceptance.retained=true \
  -DfailsafeArgLine= -pl itest/quarkus-extension-integration-test \
  failsafe:integration-test failsafe:verify
```

The empty `failsafeArgLine` removes only the inherited test-JVM coverage-agent
argument: direct Failsafe goals do not initialize the lifecycle's JaCoCo agent.
It does not skip tests. Copy the acceptance application's `target/failsafe-reports`
directory before rerunning if you need reports from both profiles. Use the lifecycle
commands when changing application or extension code; direct goals reuse the existing
packaged application. The acceptance README also describes a full lifecycle run with
the retained-cache profile.

Do not use `-DskipITests` for acceptance: it removes the integration-test aggregator.
Run native acceptance serially, without parallel Maven builds. The supported native
builder baseline is Mandrel/GraalVM 25.x. Record the resolved builder image digest
and version when validating: the `jdk-25` image tag can advance.

JVM success does not establish native compatibility. The acceptance application's
demo engine fixture does not validate a production engine adapter; validate your own
application payloads and adapter in its packaged native image.
