package dev.bpmcrafters.processengine.worker.quarkus

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import dev.bpmcrafters.processengine.worker.registrar.workerMetadata
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

class WorkerMethodsTest {
  @Test
  fun retainsConcreteGenericSignatureAndInterfaceMetadataWithoutBridgeDuplicates() {
    val methods = WorkerMethods.discover(StringWorker::class.java)
    assertThat(methods).hasSize(1)
    val method = methods[0]
    assertThat(method.parameterTypes).containsExactly(String::class.java)
    assertThat(method.workerMetadata()!!.declaringClass).isEqualTo(GenericWorker::class.java)
    assertThat(WorkerMethods.resolve(StringWorker::class.java, WorkerMethods.descriptor(StringWorker::class.java, method)))
      .isEqualTo(method)
  }

  @Test
  fun inheritsSuperclassMetadataButNotAnUnrelatedOverload() {
    assertThat(WorkerMethods.discover(Child::class.java).map { it.name }).containsExactly("work")
  }

  @Test
  fun rejectsNonPublicAndStaticWorkers() {
    assertThatIllegalArgumentException().isThrownBy { WorkerMethods.discover(PrivateWorker::class.java) }
      .withMessageContaining("public instance")
    assertThatIllegalArgumentException().isThrownBy { WorkerMethods.discover(StaticWorker::class.java) }
      .withMessageContaining("public instance")
  }

  @Test
  fun missingDescriptorIsClear() {
    assertThatIllegalArgumentException().isThrownBy { WorkerMethods.resolve(StringWorker::class.java, "missing") }
      .withMessageContaining("Worker method not found")
  }

  @Test
  fun rejectsErasedInheritedSignaturesRatherThanConvertingPayloadsToObject() {
    assertThatIllegalArgumentException().isThrownBy { WorkerMethods.discover(ErasedChild::class.java) }
      .withMessageContaining("override inherited generic methods with concrete types")
  }

  @Test
  fun validatesGenericArraysWildcardsAndReturnSignatures() {
    for (type in listOf(GenericArray::class.java, GenericUpperBound::class.java, GenericLowerBound::class.java, GenericReturn::class.java)) {
      assertThatIllegalArgumentException().isThrownBy { WorkerMethods.discover(type) }.withMessageContaining("concrete signature")
    }
    assertThat(WorkerMethods.discover(ConcreteWildcard::class.java)).hasSize(1)
  }

  class GenericArray<T> {
    @ProcessEngineWorker
    fun work(@Variable("value") value: Array<T>) = Unit
  }

  class GenericUpperBound<T> {
    @ProcessEngineWorker
    fun work(@Variable("value") value: List<@JvmWildcard T>) = Unit
  }

  class GenericLowerBound<T> {
    @ProcessEngineWorker
    fun work(@Variable("value") value: MutableList<in T>) = Unit
  }

  class GenericReturn<T> {
    @ProcessEngineWorker
    fun work(): T? = null
  }

  class ConcreteWildcard {
    @ProcessEngineWorker
    fun work(): Map<String, *> = emptyMap<String, Any?>()
  }

  open class GenericParent<T> {
    @ProcessEngineWorker
    fun work(@Variable("value") value: T) = Unit
  }

  class ErasedChild : GenericParent<String>()

  interface GenericWorker<T> {
    @ProcessEngineWorker(topic = "generic")
    fun work(@Variable("value") value: T): Map<String, Any?>
  }

  class StringWorker : GenericWorker<String> {
    override fun work(value: String): Map<String, Any?> = mapOf("value" to value)
    fun work(unrelated: Int) = Unit
  }

  open class Parent {
    @ProcessEngineWorker
    open fun work() = Unit
  }

  class Child : Parent() {
    override fun work() = Unit
  }

  class PrivateWorker {
    @ProcessEngineWorker
    private fun work() = Unit
  }

  class StaticWorker {
    companion object {
      @JvmStatic
      @ProcessEngineWorker
      fun work() = Unit
    }
  }
}
