package dev.bpmcrafters.processengine.worker.quarkus

import io.quarkus.runtime.annotations.ConfigPhase
import io.quarkus.runtime.annotations.ConfigRoot
import io.smallrye.config.ConfigMapping
import io.smallrye.config.WithDefault
import java.util.*

@ConfigMapping(prefix = "dev.bpm-crafters.process-api.worker")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
interface WorkerConfig {
  /** Enable the worker runtime. Disabled applications do not resolve engine API beans. */
  @WithDefault("true")
  fun enabled(): Boolean

  /** Register discovered CDI worker methods as task subscriptions. */
  @WithDefault("true")
  fun registerProcessWorkers(): Boolean

  /** Complete tasks before committing owned worker transactions by default. */
  @WithDefault("false")
  fun completeTasksBeforeCommit(): Boolean

  /** Remove cached task results after successful automatic completion. */
  @WithDefault("true")
  fun removeTaskResultOnCompletion(): Boolean

  /** Default tenant restriction, unless a worker annotation specifies a tenant. */
  fun tenantId(): Optional<String>
}
