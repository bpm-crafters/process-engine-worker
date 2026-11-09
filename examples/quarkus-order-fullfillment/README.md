# Java 17 Quarkus order fulfillment

This application ports the [Spring order-fulfillment example](../order-fulfillment)
to Java 17 and CDI with Quarkus 3.40 LTS (3.40.1.1) and a **real Camunda
8.8.2 engine**. It uses the published framework-neutral Camunda adapter, not Spring
or an in-memory engine. Payment, inventory and shipment remain illustrative business
services: total `42.00` rejects payment; an item named `out-of-stock` replenishes
after 60 seconds; shipment logs the destination.

## Hexagonal structure

This single Maven module uses package-level ports and adapters under
`dev.bpmcrafters.example.quarkus`:

- `domain`: immutable order aggregate, validated address/position values and results.
- `application.port.in` and `application.usecase`: receive orders, fulfill orders,
  manage notifications and deploy the packaged process.
- `application.port.out`: domain-oriented publication, fulfillment services,
  notification store/completion and deployment contracts.
- `adapter.in.rest` / `adapter.in.worker`: transport validation, explicit wire records
  and mappings, HTTP responses and worker payloads/errors.
- `adapter.out.camunda`, `adapter.out.memory`, `adapter.out.simulation`: published
  engine APIs, reconstructible leased task view and illustrative business services.
- `configuration`: CDI producers compose plain Java use cases; separate infrastructure
  lifecycle manages client ownership and delivery ordering.

Dependencies point inward: input adapters call in ports, use cases implement those
ports and depend only on domain/out ports. Domain and application have no framework,
SDK, worker or protocol-map dependencies. ArchUnit checks these actual dependencies.
Explicit wire records keep aggregate behavior out of serialized process variables.
The artifact is `process-engine-worker-example-quarkus-order-fullfillment`.

Maven packages the **unchanged shared** `../order-fulfillment/src/main/resources/C8/Order Fulfillment.bpmn`
as `C8/Order Fulfillment.bpmn`; it does not package Spring configuration or C7 models.
The process retains its existing legacy job-worker user tasks. Camunda 8.8 supports
but deprecates this implementation; modern 8.8 lint rules warn about the two missing
`zeebe:UserTask` extensions. This port does not modernize the model. Complete these
tasks through this application's endpoints, **not** the Orchestration REST user-task
API or modern Tasklist task management. The external form keys do not require new
Camunda Forms here.

## Start the local engine

Prerequisites: JDK 17 or 21, Docker with Compose **2.24+** (`configs.content` support),
and enough memory for Camunda and Elasticsearch (allow at least 4 GB for Docker).
The two-service stack pins Camunda 8.8.2 and Elasticsearch 8.17.10. Elasticsearch and
health ports stay internal; published ports bind only to `127.0.0.1`.

From this directory:

```shell
docker compose up -d
```

`.env` sets the default gRPC/REST host ports to 26500/8088. **Do not stop or reuse
an existing cluster** to free these ports. Instead create an isolated project:

```shell
CAMUNDA_GRPC_PORT=27500 CAMUNDA_REST_PORT=8188 \
  docker compose -p quarkus-order-fulfillment-isolated up -d --wait --wait-timeout 420
export EXAMPLE_CAMUNDA_GRPC_ADDRESS=http://localhost:27500
export EXAMPLE_CAMUNDA_REST_ADDRESS=http://localhost:8188
```

Use the same project name and port overrides for subsequent Compose commands.
Operate is available at the REST address (`demo` / `demo`). The local APIs deliberately
disable authentication and authorization; the application uses a no-op credentials
provider. **This is not a production security configuration.** Do not expose this
stack or the application's unauthenticated HTTP endpoints publicly. Production needs
authentication, authorization, secure networking, persistent task views and operational
hardening. The stack keeps primary engine data and Elasticsearch data in project volumes.

## Build and run

From the repository root:

```shell
./mvnw -B -ntp -pl quarkus-extension/deployment,examples/quarkus-order-fullfillment -am clean install -DskipITests
java -jar examples/quarkus-order-fullfillment/target/quarkus-app/quarkus-run.jar
```

The environment overrides above select alternate engine ports. Without overrides the
application uses `http://localhost:26500` and `http://localhost:8088`. Equivalent
configuration keys are `example.camunda.grpc-address` and `example.camunda.rest-address`.
The three workers register before delivery starts. Deployment runs automatically at
startup. The default SDK client provides four execution threads for service workers.
A separate, CDI-qualified notification client provides one independent execution
thread for the nonblocking user-task handlers and handles user-task completion.
Both clients use the same engine addresses and shared subscription repository;
four waiting inventory handlers cannot starve notification delivery. User-task leases last
60 seconds and refresh every 5 seconds, including retries after transient refresh
failures. Shutdown stops refreshing and releases user tasks, drains extension
deliveries, then closes both owned SDK clients exactly once. Qualified disposers
provide fallback cleanup; a failure closing one client does not prevent closing the other.

### Service-worker capacity and leases

The service client sets `maxJobsActive=4` **per worker**, matching its four execution
threads. The published adapter inherits this client default for each of the three
topics (`retrievePayment`, `fetchGoods`, `shipGoods`), so all service workers together
can admit at most 12 unhandled jobs into the shared pool. Extra orders wait at the
engine rather than building an unbounded local inventory queue. Increasing activation
capacity alone does not increase the four-thread pool's throughput.

Configure the inventory wait with `example.inventory.replenishment-seconds`
(`EXAMPLE_INVENTORY_REPLENISHMENT_SECONDS`), which defaults to 60. The bridge calculates
the service-task lease in seconds as `max(120, 3 * inventoryDelay + 60)`:

- Twelve admitted jobs across four execution threads require at most three execution
  waves in this conservative budget. Each wave budgets a full inventory delay, even
  though payment and shipment normally finish quickly.
- The extra 60 seconds allows for completion and other overhead. The default lease
  therefore lasts **240 seconds**, not 120 seconds.
- The bridge rejects negative delays and arithmetic overflow, including overflow
  when the adapter converts the lease to milliseconds.

This budget is not an absolute latency SLA: slow infrastructure, pauses or handler
changes can exhaust the margin. Camunda uses at-least-once job delivery; lease expiry
allows redelivery, so production handlers still need idempotent side effects. Revisit
both capacity and the lease budget when adding a service topic or changing handler
timing. The separate notification client's one execution thread, 60-second user-task
leases and refresh schedule remain independent of this service-task budget.

See the Camunda 8.8 [Java job-worker documentation](https://docs.camunda.io/docs/8.8/apis-tools/java-client/job-worker/)
for per-worker activation limits and
[worker sizing guidance](https://docs.camunda.io/docs/8.8/components/best-practices/development/writing-good-workers/#size-maxjobsactive-against-execution-threads)
for sizing capacity against execution threads, worst-case handler duration and leases.

**Do not run the Spring example and this application against the same cluster at the
same time:** they consume the same service-task topics. Use separate Compose projects.

For development mode, run `../../mvnw quarkus:dev` from this directory after installation.
To redeploy the one allowed shared model manually:

```shell
curl --fail -H 'Content-Type: application/json' \
  -d '{"path":"C8/Order Fulfillment.bpmn"}' http://localhost:8080/deploy
```

The response is the adapter's `DeploymentInformation`, as in the Spring example.
Other paths return HTTP 400 rather than exposing arbitrary classpath resources.

## Exercise the three scenarios

Use [order-fulfillment.http](order-fulfillment.http) in an HTTP client, or send:

```shell
curl --fail -H 'Content-Type: application/json' \
  -d '{"id":"d61a74aa-cb7e-48ed-a092-b539f9dc2eba",
       "shipmentAddress":{"streetLine":"Main 1","zipCode":"12345","city":"Berlin","country":"DE"},
       "invoiceAddress":{"streetLine":"Main 1","zipCode":"12345","city":"Berlin","country":"DE"},
       "orderPositions":[{"name":"book","amount":2,"price":10.00}]}' \
  http://localhost:8080/ingress/order-received
```

The endpoint returns **204**, not an invented process instance ID: the adapter's
message-start API returns an empty ID. The HTTP `id` and `shipmentAddress` fields map
to domain `order.orderId` and `order.shippingAddress` variables. Use a new UUID for
each request. Quantities are positive integers and prices/totals use decimal arithmetic.
Missing, null or invalid order fields return HTTP 400.

1. **Happy path:** keep the payload above. The process collects payment, fetches goods (`shipped=true`) and ships (`goodsShipped=true`). Inspect the completed
   instance
   and variables in Operate or the engine's `/v2/process-instances/search` and
   `/v2/variables/search` APIs.
2. **Payment failure:** use a new UUID and set price to `21.00` with amount `2`.
   Total `42.00` throws BPMN error `paymentFailed` with `paymentReceived=false`,
   `paymentFailedReason` and `paymentAmount`. Fetch `/user-tasks`, find your order's
   `inform_customer_failed_payment` task, then GET and POST
   `/failed-payment/{taskId}`. The process ends without shipment.
3. **Inventory delay:** use a new UUID and price `10.00`, name `out-of-stock`.
   The non-interrupting PT5S timer creates `inform_customer_delay` while inventory
   waits. Fetch `/user-tasks`, then GET and POST `/delayed-inventory/{taskId}`.
   Inventory eventually replenishes and shipment completes. Override
   `EXAMPLE_INVENTORY_REPLENISHMENT_SECONDS` to change the default 60-second wait.

`GET /user-tasks` returns task IDs, activity types and order UUIDs derived from the
payload, not business-key metadata. Detail endpoints return the typed order plus
failure information or delayed item names. Both completion endpoints return 204 and
write `{"customer-informed":true}`. Unknown tasks and mismatched task types return

404. Failed engine completion retains the local task for retry. The in-memory view
     is not a durable task database; engine leases allow reconstruction after restart.

## Offline and real-engine tests

Normal unit/HTTP tests never contact a broker. `%test` independently disables worker
registration, automatic deployment, and `example.camunda.startup-enabled`; the last
flag gates lazy CDI lookups before resolving clients or deliveries. Only test sources
provide API stubs. Tests cover decimal payment rejection, manual completion,
inventory routing/interruption, task validation/deduplication/removal, failed completion retention,
HTTP input validation and refresh exception containment.
HTTP tests also capture the message-start command and mapped domain order, verify
manual deployment sends the packaged model bytes, and reject other resource paths
without calling the deployment API.
Lifecycle tests also check registration-before-activation, disabled lazy lookups,
observer priorities, pool unsubscription, notification-client qualification, concurrent
client ownership, and exactly-once closure of both clients through shutdown or disposer
fallback, including close failures.
Offline capacity tests construct the production SDK client with inert loopback
addresses and capture the published adapter's real worker builders before `open()`
can start delivery. They verify the effective four-job per-worker limit, the
nine-order and aggregate three-topic lease budgets, and invalid-delay rejection
without waiting for inventory or contacting an engine.

The opt-in `real-engine` Maven profile runs `OrderFulfillmentIT` against the packaged **JVM** application and your already-running Compose project. It sends
HTTP ingress
requests for all three scenarios, polls persisted engine variables and completed
instances with bounded deadlines, and completes both notification types. It uses UUID
correlation and compares persisted completed activity timestamps: the notification
starts and completes during the inventory wait, and shipment starts after inventory
completes. The regression `fourConcurrentInventoryWaitsDoNotStarveNotifications`
also submits four distinct out-of-stock orders concurrently, waits for all four
inventory activities to be active, and completes all four notifications through the
application API. It requires every persisted notification completion timestamp to
precede the earliest inventory completion across all four orders. This avoids treating
missing, eventually consistent search results as concurrency proof and detects a shared
executor that dispatches notifications only after an inventory worker becomes free.
Keep the replenishment delay at 60 seconds for these timing checks.
The fifth scenario,
`nineConcurrentInventoryOrdersCompleteNotificationsAndFinishWithoutIncidents`,
submits nine concurrent out-of-stock orders, completes their notifications and checks
that all nine instances finish without incidents. It uses a shared 300-second polling
budget to accommodate three inventory waves; the original four scenarios retain
their 150-second polling deadlines and the four-order timestamp oracle above.
The bulk scenario does not require all nine inventory activities to become active
together: capacity limits may legitimately queue payment jobs first. It uses unique
UUIDs and attempts notification cleanup on failure. Completed instances alone do
**not** prove zero lease expiry or redelivery; the deterministic offline tests protect
the configured capacity/lease relationship, not runtime scheduling guarantees.
It never creates, stops or cleans up Docker containers itself.

From the repository root, after starting your isolated stack:

```shell
EXAMPLE_CAMUNDA_GRPC_ADDRESS=http://localhost:27500 \
EXAMPLE_CAMUNDA_REST_ADDRESS=http://localhost:8188 \
EXAMPLE_INVENTORY_REPLENISHMENT_SECONDS=60 \
  ./mvnw -B -ntp -P real-engine -DskipITests \
  -pl quarkus-extension/deployment,examples/quarkus-order-fullfillment -am verify
```

The environment config reaches both the packaged application and the engine search
assertions. Do not point the profile at unrelated/shared clusters. Tests leave completed
instances in the engine for inspection; repeat runs use new UUIDs. This profile does
not establish native compatibility of the real SDK/adapter.

Stop the application first, then clean **only your own project** (from this directory):

```shell
CAMUNDA_GRPC_PORT=27500 CAMUNDA_REST_PORT=8188 \
  docker compose -p quarkus-order-fulfillment-isolated down
# Add --volumes only when you want to delete that project's persisted example data.
```

See the [Quarkus guide](../../docs/quarkus.md) for extension configuration,
transactions, deployment and optional persistent idempotency storage.

## JVM and native validation

The real-engine example above has JVM-scoped validation. The following commands
validate the separate
[packaged acceptance application](../../itest/quarkus-extension-integration-test/README.md)
at `itest/quarkus-extension-integration-test`. That application uses PostgreSQL
Testcontainers and exercises worker, JPA registry and resource-deployment behavior.
Its README describes prerequisites, runtime profiles and coverage boundaries.
The `native` Maven profile belongs to that acceptance application, not this real-engine example.

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
