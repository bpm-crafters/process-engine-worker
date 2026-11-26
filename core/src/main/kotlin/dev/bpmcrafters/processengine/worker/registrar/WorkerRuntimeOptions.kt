package dev.bpmcrafters.processengine.worker.registrar

import java.time.Duration

/**
 * Framework-neutral defaults used when registering workers.
 * @property completeTasksBeforeCommit Default ordering of task completion relative to transaction commit.
 * @property removeTaskResultOnCompletion Whether successful completion clears the idempotency result.
 * @property tenantId Default tenant for subscriptions without a tenant override.
 * @property backoffExceptions Delay by exception class for retries that keep their retry count.
 * @since 0.9.0
 **/
data class WorkerRuntimeOptions @JvmOverloads constructor(
  val completeTasksBeforeCommit: Boolean = false,
  val removeTaskResultOnCompletion: Boolean = true,
  val tenantId: String? = null,
  val backoffExceptions: Map<Class<out Throwable>, Duration> = emptyMap()
)
