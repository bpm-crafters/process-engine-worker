package dev.bpmcrafters.processengine.worker.quarkus.deployment

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.task.*
import io.quarkus.test.QuarkusExtensionTest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

class WorkerIdentityAndMetadataTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot {
      it.addClasses(
        Engine::class.java, Handle::class.java, Metadata::class.java, Blue::class.java, QualifiedWorker::class.java,
        ParentWorker::class.java, ChildWorker::class.java, Contract::class.java, Base::class.java, InheritedWorker::class.java,
        GenericContract::class.java, GenericBase::class.java, InheritedGenericWorker::class.java
      )
    }
  }

  @Inject
  lateinit var engine: Engine

  @Test
  fun resolvesQualifiedWorkerByArcIdentity() {
    engine.deliver("qualified", emptyMap())
    assertThat(engine.results["qualified"]).containsExactly("qualified")
  }

  @Test
  fun resolvesParentAndChildAsDistinctBeansDespiteAmbiguousParentType() {
    engine.deliver("identity", emptyMap())
    assertThat(engine.results["identity"]).containsExactlyInAnyOrder("parent", "child")
  }

  @Test
  fun discoversContractsOnActualBeansWithInheritedPlainImplementations() {
    engine.deliver("inherited", emptyMap())
    engine.deliver("inherited-generic", mapOf("value" to "generic-value"))
    assertThat(engine.results["inherited"]).containsExactly("inherited")
    assertThat(engine.results["inherited-generic"]).containsExactly("generic-value")
    assertThat(engine.commands.first { it.taskDescriptionKey == "inherited-generic" }.payloadDescription).containsExactly("value")
  }

  @Qualifier
  @Retention(AnnotationRetention.RUNTIME)
  @Target(AnnotationTarget.CLASS, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
  annotation class Blue

  @Blue
  @ApplicationScoped
  class QualifiedWorker {
    @ProcessEngineWorker(topic = "qualified")
    fun work(): Map<String, Any?> = mapOf("value" to "qualified")
  }

  @ApplicationScoped
  open class ParentWorker {
    @ProcessEngineWorker(topic = "identity")
    fun work(): Map<String, Any?> = mapOf("value" to label())
    open fun label() = "parent"
  }

  @ApplicationScoped
  class ChildWorker : ParentWorker() {
    override fun label() = "child"
  }

  interface Contract {
    @ProcessEngineWorker(topic = "inherited")
    fun work(): Map<String, Any?>
  }

  open class Base {
    open fun work(): Map<String, Any?> = mapOf("value" to "inherited")
  }

  @ApplicationScoped
  class InheritedWorker : Base(), Contract
  interface GenericContract<T> {
    @ProcessEngineWorker(topic = "inherited-generic")
    fun work(@Variable("value") value: T): Map<String, Any?>
  }

  open class GenericBase {
    open fun work(value: String): Map<String, Any?> = mapOf("value" to value)
    open fun work(unrelated: Int?) {
      throw AssertionError("Unannotated overload")
    }
  }

  @ApplicationScoped
  class InheritedGenericWorker : GenericBase(), GenericContract<String>
  class Handle : TaskSubscription
  class Metadata : MetaInfo

  @Singleton
  class Engine : TaskSubscriptionApi, ServiceTaskCompletionApi {
    val commands = ArrayList<SubscribeForTaskCmd>()
    val results = HashMap<String, MutableList<Any?>>()
    fun deliver(topic: String, payload: Map<String, Any?>) {
      val index = AtomicInteger()
      commands.filter { it.taskDescriptionKey == topic }.forEach {
        it.action.accept(TaskInformation("$topic:${index.incrementAndGet()}", mapOf("processInstanceId" to "process")), payload)
      }
    }

    override fun subscribeForTask(command: SubscribeForTaskCmd): CompletableFuture<TaskSubscription> {
      commands.add(command)
      return CompletableFuture.completedFuture(Handle())
    }

    override fun unsubscribe(command: UnsubscribeFromTaskCmd): CompletableFuture<Empty> = CompletableFuture.completedFuture(Empty)
    override fun getSupportedRestrictions(): Set<String> = emptySet()
    override fun meta(instance: MetaInfoAware): MetaInfo = Metadata()
    override fun completeTask(command: CompleteTaskCmd): CompletableFuture<Empty> {
      val topic = command.taskId.split(':')[0]
      results.computeIfAbsent(topic) { ArrayList() }.add(command.get()["value"])
      return CompletableFuture.completedFuture(Empty)
    }

    override fun completeTaskByError(command: CompleteTaskByErrorCmd): CompletableFuture<Empty> = throw AssertionError("Unexpected business error")
    override fun failTask(command: FailTaskCmd): CompletableFuture<Empty> = throw AssertionError(command.errorDetails)
  }
}
