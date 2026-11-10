package dev.bpmcrafters.processengine.worker.acceptance

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager.TestInjector
import java.sql.SQLException
import org.testcontainers.postgresql.PostgreSQLContainer

class PostgreSqlResource : QuarkusTestResourceLifecycleManager {
  private var postgres: PostgreSQLContainer? = null
  private lateinit var database: DatabaseAccess

  override fun start(): Map<String, String> {
    val container = PostgreSQLContainer("postgres:17-alpine")
      .withDatabaseName("worker_acceptance").withUsername("worker").withPassword("worker")
    postgres = container
    container.start()
    database = DatabaseAccess(container.jdbcUrl, container.username, container.password)
    createSchema()
    return mapOf("quarkus.datasource.jdbc.url" to database.url, "quarkus.datasource.username" to database.username,
      "quarkus.datasource.password" to database.password, "quarkus.datasource.devservices.enabled" to "false")
  }

  private fun createSchema() {
    try {
      database.open().use { connection ->
        connection.createStatement().use { statement ->
          statement.execute("create table task_log_entry_ (task_id_ varchar(100) primary key, " +
            "process_instance_id_ varchar(100) not null, created_at_ timestamp with time zone not null, result_ bytea)")
          statement.execute("create table worker_business_entry_ " +
            "(task_id_ varchar(100) primary key, value_ varchar(100) not null " +
            "constraint reject_fixture_value check (value_ <> 'reject-at-flush'))")
        }
      }
    } catch (exception: SQLException) {
      postgres?.stop()
      throw IllegalStateException("Cannot prepare acceptance database", exception)
    }
  }

  override fun inject(injector: TestInjector) {
    injector.injectIntoFields(database, TestInjector.MatchesType(DatabaseAccess::class.java))
  }

  override fun stop() { postgres?.stop() }
}
