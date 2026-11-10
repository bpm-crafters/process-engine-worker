---
title: Idempotency Registry
---

The Idempotency Registry caches worker results by task ID. A redelivery can replay a
stored result instead of invoking business logic again. This is not an exactly-once
guarantee: concurrent deliveries can both miss the cache, and successful automatic
completion removes the result by default.

## How it Works

When a worker is triggered, the process engine worker:
1. Checks the `IdempotencyRegistry` if a result already exists for the given `taskId`.
2. If a result exists, it skips the worker execution and returns the stored result.
3. If no result exists, it executes the worker.
4. After successful execution, it registers the result in the `IdempotencyRegistry`.

## Implementations

The registry contract lives in `process-engine-worker-core`. Available implementations include:

| Implementation                | Description                                       | Recommended Use                                   |
|-------------------------------|---------------------------------------------------|---------------------------------------------------|
| `NoOpIdempotencyRegistry`     | Does nothing. No results are stored or retrieved. | Default, use if idempotency is handled elsewhere. |
| `LocalInMemoryIdempotencyRegistry` | Core-only immediate local storage, without transaction synchronization. | Plain Java tests or non-transactional, non-clustered applications. |
| `InMemoryIdempotencyRegistry` | Stores results in a local `ConcurrentHashMap`.    | Testing or non-clustered environments.            |
| `JpaIdempotencyRegistry`      | Stores results in a database using JPA.           | Production, clustered environments.               |

## Setup Procedures

### In-Memory Registry

The following example uses Spring. For Quarkus, see [Quarkus registries](#quarkus-registries).

To use the in-memory registry, you need to provide a bean of type `IdempotencyRegistry` in your Spring configuration:

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

## Quarkus registries

The Quarkus extension defaults to `NoOpIdempotencyRegistry`. Supply a CDI
`IdempotencyRegistry` bean to override it. For local tests, produce
`dev.bpmcrafters.processengine.worker.quarkus.JtaInMemoryIdempotencyRegistry`
with an injected `jakarta.transaction.TransactionSynchronizationRegistry`:

```kotlin
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton
import jakarta.transaction.TransactionSynchronizationRegistry

@ApplicationScoped
class RegistryConfiguration {
  @Produces
  @Singleton
  fun registry(
    transactions: TransactionSynchronizationRegistry
  ): IdempotencyRegistry = JtaInMemoryIdempotencyRegistry(transactions)
}
```
