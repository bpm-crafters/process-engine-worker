package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BeanRelativeWorkerMetadataTest {
  @Test
  fun resolvesInterfaceIntroducedBySubclassWithInheritedImplementation() {
    val implementation = Worker::class.java.getMethod("work")
    assertThat(implementation.declaringClass).isEqualTo(Base::class.java)
    assertThat(implementation.workerMetadata()).isNull()
    assertThat(implementation.workerMetadata(Worker::class.java))
      .isEqualTo(Contract::class.java.getMethod("work"))
  }

  @Test
  fun resolvesGenericContractWithoutMatchingUnrelatedOverload() {
    val implementation = GenericWorker::class.java.getMethod("work", String::class.java)
    assertThat(implementation.declaringClass).isEqualTo(GenericBase::class.java)
    assertThat(implementation.workerMetadata(GenericWorker::class.java))
      .isEqualTo(GenericContract::class.java.getMethod("work", Any::class.java))
    assertThat(GenericWorker::class.java.getMethod("work", Int::class.javaObjectType)
      .workerMetadata(GenericWorker::class.java)).isNull()
  }

  @Test
  fun retainsDirectImplementationMetadataPrecedence() {
    val implementation = AnnotatedWorker::class.java.getMethod("work")
    assertThat(implementation.workerMetadata(AnnotatedWorker::class.java)).isEqualTo(implementation)
  }

  interface Contract {
    @ProcessEngineWorker(topic = "contract")
    fun work()
  }

  open class Base {
    open fun work() {}
  }

  class Worker : Base(), Contract

  interface GenericContract<T> {
    @ProcessEngineWorker(topic = "generic-contract")
    fun work(@Variable(name = "value") value: T)
  }

  open class GenericBase {
    fun work(value: String) {}
    fun work(unrelated: Int?) {}
  }

  class GenericWorker : GenericBase(), GenericContract<String>

  class AnnotatedWorker : Base(), Contract {
    @ProcessEngineWorker(topic = "implementation")
    override fun work() {}
  }
}
