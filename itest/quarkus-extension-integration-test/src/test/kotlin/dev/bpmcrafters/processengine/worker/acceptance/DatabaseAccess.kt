package dev.bpmcrafters.processengine.worker.acceptance

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.sql.Connection
import java.sql.DriverManager

/** Direct JDBC from the external test JVM; every operation opens a fresh connection. */
data class DatabaseAccess(val url: String, val username: String, val password: String) {
  fun open(): Connection = DriverManager.getConnection(url, username, password)

  fun businessCount(id: String): Int = open().use { connection ->
    connection.prepareStatement("select count(*) from worker_business_entry_ where task_id_ = ?").use { statement ->
      statement.setString(1, id)
      statement.executeQuery().use { rows ->
        rows.next()
        rows.getInt(1)
      }
    }
  }

  fun registryRow(id: String): RegistryRow = open().use { connection ->
    connection.prepareStatement("select process_instance_id_, result_ from task_log_entry_ where task_id_ = ?").use { statement ->
      statement.setString(1, id)
      statement.executeQuery().use { rows ->
        if (rows.next()) RegistryRow(true, rows.getString(1), rows.getBytes(2)) else RegistryRow(false, null, null)
      }
    }
  }

  fun insertJvmSerialized(id: String, result: Map<String, Any?>) {
    val bytes = ByteArrayOutputStream().use { buffer ->
      ObjectOutputStream(buffer).use { output ->
        output.writeObject(result)
        output.flush()
        buffer.toByteArray()
      }
    }
    insertBytes(id, bytes)
  }

  fun insertBytes(id: String, bytes: ByteArray) {
    open().use { connection ->
      connection.prepareStatement("insert into task_log_entry_ (task_id_, process_instance_id_, created_at_, result_) " +
        "values (?, 'serialization', current_timestamp, ?)").use { statement ->
        statement.setString(1, id)
        statement.setBytes(2, bytes)
        statement.executeUpdate()
      }
    }
  }

  fun deserialize(bytes: ByteArray?): Any = ObjectInputStream(ByteArrayInputStream(requireNotNull(bytes))).use { it.readObject() }

  data class RegistryRow(val exists: Boolean, val processId: String?, val bytes: ByteArray?)
}
