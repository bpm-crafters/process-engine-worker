package dev.bpmcrafters.processengine.worker.quarkus.deployment

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import dev.bpmcrafters.processengine.worker.registrar.VariableConverter
import io.quarkus.test.QuarkusExtensionTest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ArgumentResolutionIsolationTest {
  companion object {
    @JvmField @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot { it.addClasses(
      Worker::class.java, Converter::class.java, State::class.java, WorkerIdentityAndMetadataTest.Engine::class.java,
      WorkerIdentityAndMetadataTest.Handle::class.java, WorkerIdentityAndMetadataTest.Metadata::class.java) }
  }

  @Inject lateinit var engine: WorkerIdentityAndMetadataTest.Engine
  @Inject lateinit var state: State

  @Test fun converterOrdinaryCallCannotClaimThePendingManagedInvocation() {
    engine.deliver("argument", mapOf("value" to "delivery"))
    assertThat(state.calls).containsExactly("ordinary", "delivery")
    assertThat(engine.results["argument"]).containsExactly("delivery")
  }

  @Singleton
  class State { val calls = ArrayList<String>() }

  @ApplicationScoped
  class Worker {
    @Inject lateinit var state: State
    @ProcessEngineWorker(topic = "argument")
    fun work(@Variable("value") value: String): Map<String, Any?> {
      state.calls.add(value)
      return mapOf("value" to value)
    }
  }

  @Singleton
  class Converter : VariableConverter {
    @Inject lateinit var worker: Worker
    override fun <T : Any> mapToType(value: Any?, type: Class<T>): T {
      worker.work("ordinary")
      return type.cast(value)
    }
  }
}
