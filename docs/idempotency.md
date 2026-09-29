---
title: Idempotency Registry
---

The Idempotency Registry is a feature designed to prevent duplicate worker invocations. It ensures that if a task is processed multiple times (e.g., due to network issues or retries in the process engine), the worker logic is only executed once, and the previous result is returned for subsequent calls.

## How it Works

When a worker is triggered, the process engine worker:
1. Checks the `IdempotencyRegistry` if a result already exists for the given `taskId`.
2. If a result exists, it skips the worker execution and returns the stored result.
3. If no result exists, it executes the worker.
4. After successful execution, it registers the result in the `IdempotencyRegistry`.

## Implementations

There are three available implementations of the `IdempotencyRegistry`:

| Implementation                                                | Description                                       | Recommended Use                                   |
|---------------------------------------------------------------|---------------------------------------------------|---------------------------------------------------|
| `NoOpIdempotencyRegistry`                                     | Does nothing. No results are stored or retrieved. | Default, use if idempotency is handled elsewhere. |
| `InMemoryIdempotencyRegistry`                                 | Stores results in a local `ConcurrentHashMap`.    | Testing or non-clustered environments.            |
| `JpaIdempotencyRegistry` / `EntityManagerJpaIdempotencyRegistry` | Stores results in a database using JPA.        | Production, clustered environments.               |

The interface, the no-op and the in-memory registry are part of `process-engine-worker-core` and therefore available in all
frameworks. The JPA based registries share the module `process-engine-worker-idempotency-registry-jpa`, which contains the
`TaskLogEntry` entity (table `task_log_entry_`), the `TaskResultMapConverter` and the framework-free
`EntityManagerJpaIdempotencyRegistry`. Spring Boot adds a Spring Data based `JpaIdempotencyRegistry` on top of it
(`process-engine-worker-spring-boot-idempotency-registry-jpa`), Quarkus uses the `EntityManagerJpaIdempotencyRegistry` directly.
Both use the same database schema.

If the worker is transactional, results are registered only after the transaction has been committed (in-memory registry) or
inside the worker's transaction (JPA registries), so a rolled-back worker never leaves a result behind. Results are removed after a
successful completion if `dev.bpm-crafters.process-api.worker.remove-task-result-on-completion` is `true` (default).

### Result serialization

The JPA registries store the result map in the `result_` column as binary data. The serialization is pluggable via
`TaskResultMapSerializer.DEFAULT`:

| Serializer                       | Description                                                                                              |
|----------------------------------|----------------------------------------------------------------------------------------------------------|
| `JavaTaskResultMapSerializer`    | Default. Java serialization, all values of the result map must be `Serializable`. Compatible with previous versions. |
| `JacksonTaskResultMapSerializer` | JSON serialization using a Jackson `ObjectMapper`. Values are read back as plain JSON-compatible values (maps, lists, strings, numbers, booleans), not as the original classes. Suitable for native images. |

`TaskResultMapSerializer.DEFAULT` is a JVM-wide setting shared by all persistence units and application contexts in the same
process. To switch the serializer, set it once on startup before the first entity is persisted:

```java
TaskResultMapSerializer.setDEFAULT(new JacksonTaskResultMapSerializer(objectMapper));
```

or in Kotlin:

```kotlin
TaskResultMapSerializer.DEFAULT = JacksonTaskResultMapSerializer(objectMapper)
```

## Spring Boot

### In-Memory Registry

To use the in-memory registry, you need to provide a bean of type `IdempotencyRegistry` in your Spring configuration. The starter
installs the Spring transaction synchronization on the registry, so results are registered after commit:

```kotlin
@Configuration
class IdempotencyConfiguration {

  @Bean
  fun idempotencyRegistry(): IdempotencyRegistry = InMemoryIdempotencyRegistry()

}
```

> **Warning:** The `InMemoryIdempotencyRegistry` is not suitable for clustered environments as the state is not shared between nodes.

### JPA-based Registry

The JPA-based registry is suitable for production environments. It persists the results in the database, allowing multiple instances of the worker to share the same idempotency state.

#### 1. Add Dependency

Add the following dependency to your `pom.xml`:

```xml
<dependency>
  <groupId>dev.bpm-crafters.process-engine-worker</groupId>
  <artifactId>process-engine-worker-spring-boot-idempotency-registry-jpa</artifactId>
  <version>${process-engine-worker.version}</version>
</dependency>
```

The `JpaIdempotencyAutoConfiguration` will automatically register the `JpaIdempotencyRegistry` if an `EntityManager` is present and no other `IdempotencyRegistry` bean is defined.
The module depends on the shared `process-engine-worker-idempotency-registry-jpa` module providing the entity.

#### 2. Configure JPA

Add the `dev.bpmcrafters.processengine.worker.idempotency.TaskLogEntryRepository` with the following annotation
```java
@EnableJpaRepositories(basePackages = {
    "your.package(s)",
    "dev.bpmcrafters.processengine.worker.idempotency"
})
```

You can add the entity `dev.bpmcrafters.processengine.worker.idempotency.TaskLogEntry` either via annotation
```java
@EntityScan(basePackages = {
    "your.package(s)",
    "dev.bpmcrafters.processengine.worker.idempotency"
})
```
or via `META-INF/orm.xml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<entity-mappings
  xmlns="https://jakarta.ee/xml/ns/persistence/orm"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="https://jakarta.ee/xml/ns/persistence/orm https://jakarta.ee/xml/ns/persistence/orm/orm_3_2.xsd"
  version="3.2"
>
  <entity class="dev.bpmcrafters.processengine.worker.idempotency.TaskLogEntry">
    <table schema="your_schema" name="task_log_entry"/>
    <attributes>
      <basic name="taskId">
        <column name="task_id" column-definition="varchar2(100)"/>
      </basic>
      <basic name="processInstanceId">
        <column name="process_instance_id" column-definition="varchar2(100)"/>
      </basic>
      <basic name="createdAt">
        <column name="created_at"/>
      </basic>
      <basic name="result">
        <column name="result"/>
      </basic>
    </attributes>
  </entity>
</entity-mappings>
```
Using the `orm.xml` could be useful. E.g., when you
* are using Hibernate's schema validation and the default types mismatch.
* want to relocate the entity into a different schema.
* want to rename the table.
* want to rename columns.

#### 3. Database Schema (Liquibase)

The JPA registry requires a table named `task_log_entry_`. You can use the following Liquibase changeSet to create it:

```yaml
databaseChangeLog:
  - changeSet:
      id: create-idempotency-table
      author: bpm-crafters
      changes:
        - createTable:
            tableName: task_log_entry_
            columns:
              - column:
                  name: task_id_
                  type: varchar(100)
                  constraints:
                    nullable: false
                    primaryKey: true
                    primaryKeyName: task_log_entry_pk_
              - column:
                  name: process_instance_id_
                  type: varchar(100)
                  constraints:
                    nullable: false
              - column:
                  name: created_at_
                  type: timestamp
                  constraints:
                    nullable: false
              - column:
                  name: result_
                  type: blob # or bytea for PostgreSQL
        - createIndex:
            indexName: idx_task_log_entry_process_instance_id_
            tableName: task_log_entry_
            columns:
              - column:
                  name: process_instance_id_
```

> **Note:** The `result_` column type should be suitable for storing binary data (e.g., `blob` for most databases, `bytea` for PostgreSQL).

The same changeSet (or an equivalent Flyway migration) is used for Quarkus, since both frameworks share the entity.

## Quarkus

### In-Memory Registry

Provide the registry via a CDI producer. It replaces the `@DefaultBean` no-op registry of the extension. If `quarkus-narayana-jta`
is present, the extension installs the JTA after-commit hook on the registry automatically, so results of transactional workers are
registered only after a successful commit:

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

> **Warning:** The `InMemoryIdempotencyRegistry` is not suitable for clustered environments as the state is not shared between nodes.

### JPA-based Registry

#### 1. Add Dependencies

Add the shared JPA module together with Hibernate ORM, JTA and a JDBC driver to your `pom.xml`:

```xml
<dependency>
  <groupId>dev.bpm-crafters.process-engine-worker</groupId>
  <artifactId>process-engine-worker-idempotency-registry-jpa</artifactId>
  <version>${process-engine-worker.version}</version>
</dependency>
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-hibernate-orm</artifactId>
</dependency>
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-narayana-jta</artifactId>
</dependency>
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-jdbc-postgresql</artifactId> <!-- or any other driver -->
</dependency>
```

As soon as Hibernate ORM (enabled, i.e. `quarkus.hibernate-orm.enabled` not set to `false`) and the module are present, the worker
extension produces an `EntityManagerJpaIdempotencyRegistry` bound to the `EntityManager` of the default persistence unit and the JTA
`TransactionalExecutor` instead of the no-op registry. The produced registry is a `@DefaultBean`, so an own `IdempotencyRegistry`
bean (e.g. via `@Produces`) still takes precedence. Using the JPA registry without `quarkus-narayana-jta` fails the build, and
the registry requires the default persistence unit (a default datasource): if only named persistence units are configured, the
application fails at startup with `PROCESS-ENGINE-WORKER-036` — provide an own `IdempotencyRegistry` bean in that case.

#### 2. Configure JPA

No `@EntityScan` equivalent is needed: the extension adds the entity `dev.bpmcrafters.processengine.worker.idempotency.TaskLogEntry`
and its converter to the default persistence unit at build time. Registry reads and writes join the transaction of a transactional
worker or run in their own transaction otherwise.

To relocate the entity into a different schema or rename the table or columns, use a `META-INF/orm.xml` as shown in the Spring Boot
section above and reference it via `quarkus.hibernate-orm.mapping-files`.

#### 3. Database Schema

Create the `task_log_entry_` table using the Liquibase changeSet above (`quarkus-liquibase`), an equivalent Flyway migration
(`quarkus-flyway`) or, for development only, let Hibernate generate it:

```properties
quarkus.hibernate-orm.schema-management.strategy=drop-and-create
```

#### 4. Native images

Java serialization is not available for arbitrary classes in native images. Switch the result serializer to Jackson on startup:

```java
@Singleton
public class IdempotencySerializerConfiguration {

  void onStart(@Observes StartupEvent event, ObjectMapper objectMapper) {
    TaskResultMapSerializer.setDEFAULT(new JacksonTaskResultMapSerializer(objectMapper));
  }
}
```

> **Note:** Results stored with the Java serializer can not be read with the Jackson serializer and vice versa. Switch the serializer
> only on an empty `task_log_entry_` table.
