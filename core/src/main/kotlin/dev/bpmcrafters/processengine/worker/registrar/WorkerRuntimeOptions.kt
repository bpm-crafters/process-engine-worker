package dev.bpmcrafters.processengine.worker.registrar

import java.time.Duration

/** Framework-neutral defaults used when registering workers. */
data class WorkerRuntimeOptions @JvmOverloads constructor(
  val completeTasksBeforeCommit: Boolean = false,
  val removeTaskResultOnCompletion: Boolean = true,
  val tenantId: String? = null,
  val backoffExceptions: Map<Class<out Throwable>, Duration> = emptyMap()
)
