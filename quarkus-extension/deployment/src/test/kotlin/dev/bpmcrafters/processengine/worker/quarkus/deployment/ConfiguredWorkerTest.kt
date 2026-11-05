package dev.bpmcrafters.processengine.worker.quarkus.deployment

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengineapi.task.TaskInformation
import io.quarkus.test.QuarkusExtensionTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.transaction.Status
import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ConfiguredWorkerTest {
  companion object {
    @JvmField @RegisterExtension
    val APP = QuarkusExtensionTest()
      .withApplicationRoot { it.addClasses(Worker::class.java, WorkerExtensionTest.Engine::class.java,
        WorkerExtensionTest.RegistryProducer::class.java, WorkerExtensionTest.TestSubscription::class.java,
        WorkerExtensionTest.TestMetadata::class.java, WorkerExtensionTest.Outcome::class.java) }
      .overrideRuntimeConfigKey("dev.bpm-crafters.process-api.worker.tenant-id", "configured-tenant")
      .overrideRuntimeConfigKey("dev.bpm-crafters.process-api.worker.complete-tasks-before-commit", "true")
      .overrideRuntimeConfigKey("dev.bpm-crafters.process-api.worker.remove-task-result-on-completion", "false")
  }

  @Inject lateinit var engine: WorkerExtensionTest.Engine
  @Inject lateinit var registry: IdempotencyRegistry

  @Test fun appliesTenantDefaultCompletionTimingAndResultRetention() {
    val task = TaskInformation("configured-task", mapOf("processInstanceId" to "process"))
    engine.deliver("configured", task, emptyMap())
    assertThat(engine.subscriptions["configured"]!!.restrictions).containsEntry("tenantId", "configured-tenant")
    assertThat(engine.completionStatuses[task.taskId]).isEqualTo(Status.STATUS_ACTIVE)
    assertThat(registry.getTaskResult(task)).containsEntry("configured", true)
  }

  @Singleton
  class Worker {
    @Transactional @ProcessEngineWorker(topic = "configured")
    fun work(): Map<String, Any?> = mapOf("configured" to true)
  }
}
