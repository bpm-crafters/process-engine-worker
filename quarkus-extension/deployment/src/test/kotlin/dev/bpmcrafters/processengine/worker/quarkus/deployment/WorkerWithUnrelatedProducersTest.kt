package dev.bpmcrafters.processengine.worker.quarkus.deployment

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengineapi.task.TaskInformation
import io.quarkus.test.QuarkusExtensionTest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class WorkerWithUnrelatedProducersTest {
  companion object {
    @JvmField @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot { it.addClasses(
      Worker::class.java, RecordingRegistry::class.java,
      WorkerIdentityAndMetadataTest.Engine::class.java, WorkerIdentityAndMetadataTest.Handle::class.java,
      WorkerIdentityAndMetadataTest.Metadata::class.java) }
  }

  @Inject lateinit var engine: WorkerIdentityAndMetadataTest.Engine
  @Inject lateinit var worker: Worker
  @Inject lateinit var registry: IdempotencyRegistry

  @Test fun registersManagedWorkerOnceAndUsesItsProducedRegistryDuringDelivery() {
    assertThat(engine.commands.map { it.taskDescriptionKey }).containsExactly("producer-owner")
    engine.deliver("producer-owner", emptyMap())
    assertThat(worker.invocations).isEqualTo(1)
    assertThat(engine.results["producer-owner"]).containsExactly("managed")
    assertThat(registry).isSameAs(worker.customRegistry)
    assertThat(worker.customRegistry.lookups).containsExactly("producer-owner:1")
    assertThat(worker.customRegistry.registrations).containsExactly(mapOf("value" to "managed"))
  }

  @ApplicationScoped
  class Worker {
    val customRegistry = RecordingRegistry()
    var invocations = 0
    @Produces @Singleton fun registry(): IdempotencyRegistry = customRegistry
    @Produces @JvmField final val unrelated: String = "not a worker"
    @ProcessEngineWorker(topic = "producer-owner")
    fun work(): Map<String, Any?> {
      invocations++
      return mapOf("value" to "managed")
    }
  }

  class RecordingRegistry : IdempotencyRegistry {
    val lookups = ArrayList<String>()
    val registrations = ArrayList<Map<String, Any?>>()
    override fun getTaskResult(taskInformation: TaskInformation): Map<String, Any?>? {
      lookups.add(taskInformation.taskId)
      return null
    }
    override fun register(taskInformation: TaskInformation, result: Map<String, Any?>) { registrations.add(result) }
    override fun removeTaskResult(taskId: String) {}
    override fun removeAllTaskResults(processInstanceId: String) {}
  }
}
