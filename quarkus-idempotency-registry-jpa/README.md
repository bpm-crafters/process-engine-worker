# Quarkus JPA idempotency registry

Add this optional extension to cache worker results in a relational database:

```xml
<dependency>
  <groupId>dev.bpm-crafters.process-engine-worker</groupId>
  <artifactId>process-engine-worker-quarkus-idempotency-registry-jpa</artifactId>
  <version>${process-engine-worker.version}</version>
</dependency>
```

Also add your application's Quarkus JDBC extension, for example
`io.quarkus:quarkus-jdbc-postgresql`, and configure the default datasource:

```properties
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=worker
quarkus.datasource.password=${DATABASE_PASSWORD}
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/worker
quarkus.hibernate-orm.schema-management.strategy=validate
```

The extension supplies no production database driver. It uses the **default
persistence unit**, including when `quarkus.hibernate-orm.packages` restricts
application entity discovery. Named persistence units are not configurable for
this registry. Do not map the same table with the Spring registry entity in the
same persistence unit.

## Schema and transactions

Manage the schema with your application's migrations. For PostgreSQL, create:

```sql
create table task_log_entry_ (
  task_id_ varchar(100) primary key,
  process_instance_id_ varchar(100) not null,
  created_at_ timestamp with time zone not null,
  result_ bytea
);
```

The table and serialized payload format match the Spring JPA registry. The binary
column uses `bytea`, not a PostgreSQL large-object OID. Size the binary column
appropriately on other databases. Each task requires String process-instance
metadata under `CommonRestrictions.PROCESS_INSTANCE_ID`.

The registry joins an active business transaction. A rollback undoes its writes
and deletions. Calls from nontransactional worker threads start a transaction;
after-completion cleanup suspends any completed transaction association before
starting a new one. Programmatic transaction boundaries also cover internal
calls; they do not depend on interceptor self-invocation.

Re-registering a task updates its process ID, timestamp, and result. A missing row
returns `null`; an existing row with an empty result stores SQL `NULL` and returns
an empty map. Single-task and process-instance cleanup are safe to repeat.
Use `removeAllTaskResults(processInstanceId)` when a process ends, or implement
retention with application-controlled cleanup. This cache does **not** guarantee
atomic duplicate suppression across concurrent workers.

An application `IdempotencyRegistry` CDI bean overrides the JPA default. The JPA
default overrides the worker extension's no-op default. Supply a single CDI
`Clock` bean to customize timestamps; otherwise the registry uses a private UTC
system clock.

## Native images and Java serialization

The converter writes the original map with `ObjectOutputStream`; it does not
normalize collections or switch to JSON. Only deserialize trusted database
payloads. Do not pass untrusted network streams to this converter.

The extension registers serialization support for common Java mutable,
singleton, empty, immutable, and unmodifiable maps/lists/sets; Kotlin empty and
built collections; primitive wrappers, String, BigDecimal, BigInteger, UUID,
Date, and common `java.time` values. Registration includes serialization proxies
such as `java.util.CollSer`, `java.time.Ser`, and Kotlin's serialized collection
delegates. It does not scan all application classes or infer arbitrary map
contents.

The bounded array metadata covers one and two dimensions of primitive types,
Object, String, primitive wrappers, Number, BigDecimal, BigInteger, UUID, Date,
and the public `java.time` scalar types listed by the extension. This includes
JVM descriptors such as `[[I`, `[[Ljava.lang.Object;`, and `[Ljava.lang.Integer;`:
native deserialization needs class lookup as well as serialization support for
these exact array types. Register arrays with other component types or more than
two dimensions explicitly in the application; the extension does not infer them.

The extension also registers the bounded Throwable/Exception/I/O exception chain,
StackTraceElement arrays, and already-supported empty-list/ArrayList containers
needed by `ObjectOutputStream`'s standard nonserializable-value abort protocol.
This preserves the converter's `Cannot serialize task result` diagnostic and
`NotSerializableException` cause in native images. It does not promise support
for arbitrary exception payloads or custom exception subclasses.

Register every custom, nested, and polymorphic payload class explicitly:

```kotlin
@io.quarkus.runtime.annotations.RegisterForReflection(serialization = true)
data class WorkerResult(val reference: String) : java.io.Serializable
```

Register custom DTO array types separately from their component class:

```kotlin
@io.quarkus.runtime.annotations.RegisterForReflection(
  targets = [Array<WorkerResult>::class, Array<Array<WorkerResult>>::class],
  serialization = true
)
class WorkerResultArrayRegistration
```

Register additional third-party collection types, custom enum types, and custom
serialization proxies in the application as needed. Keep every class needed to
read **historical database payloads** available and registered in the native
image, even if current workers no longer produce that type. Preserve compatible
class names and `serialVersionUID` values across deployments. JVM round-trip
tests cannot replace a packaged native test with the application's real payloads.

## Build and validation

The aggregator inherits Java 17 compiler configuration and the Quarkus BOM from
`../quarkus-extension/pom.xml`. Maven does not inherit the parent's module list;
this aggregator declares only its own runtime and deployment modules. From the
repository root, run the focused registry tests:

```shell
./mvnw -DskipExamples -DskipITests \
  -pl quarkus-idempotency-registry-jpa/runtime,quarkus-idempotency-registry-jpa/deployment \
  -am verify
```

The runtime tests check byte compatibility, nested containers, and invalid
payloads. Deployment tests use real Hibernate ORM, H2, Narayana transactions,
CDI override resolution, restrictive entity-package discovery, and after-commit
cleanup. Use the [packaged acceptance application](../itest/quarkus-extension-integration-test/README.md)
for PostgreSQL and native validation. Also test your application with its own JDBC
extension, engine adapter and actual worker payloads. See the
[Quarkus guide](../docs/quarkus.md) and [runnable example](../examples/quarkus-order-fullfillment/README.md)
for worker setup.
