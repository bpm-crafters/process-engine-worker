package dev.bpmcrafters.processengine.worker.quarkus.deployment

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import io.quarkus.test.QuarkusExtensionTest
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ProducedWorkerMethodTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot { it.addClasses(Factory::class.java, Worker::class.java) }
      .assertException { assertThat(it).hasStackTraceContaining("Worker producer beans are unsupported") }
  }

  @Test
  fun rejectsWorkerReturnedByProducerMethod() {
  }

  @Singleton
  class Factory {
    @Produces
    @Singleton
    fun worker(): Worker = Worker()
  }

  class Worker {
    @ProcessEngineWorker
    fun work(): Map<String, Any?> = emptyMap()
  }
}

class ProducedWorkerFieldTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot { it.addClasses(Factory::class.java, Worker::class.java) }
      .assertException { assertThat(it).hasStackTraceContaining("Worker producer beans are unsupported") }
  }

  @Test
  fun rejectsWorkerStoredInProducerField() {
  }

  @Singleton
  class Factory {
    @Produces
    @Singleton
    @JvmField
    final val worker = Worker()
  }

  class Worker {
    @ProcessEngineWorker
    fun work(): Map<String, Any?> = emptyMap()
  }
}

class ProducedInterfaceWorkerTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot {
      it.addClasses(
        Factory::class.java, Contract::class.java, Worker::class.java
      )
    }
      .assertException { assertThat(it).hasStackTraceContaining("Worker producer beans are unsupported") }
  }

  @Test
  fun rejectsProducerReturningAnnotatedWorkerInterface() {
  }

  @Singleton
  class Factory {
    @Produces
    @Singleton
    fun worker(): Contract = Worker()
  }

  interface Contract {
    @ProcessEngineWorker
    fun work(): Map<String, Any?>
  }

  class Worker : Contract {
    override fun work(): Map<String, Any?> = emptyMap()
  }
}

class ProducedInheritedWorkerTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot {
      it.addClasses(
        Factory::class.java, Contract::class.java, Base::class.java, Worker::class.java
      )
    }
      .assertException { assertThat(it).hasStackTraceContaining("Worker producer beans are unsupported") }
  }

  @Test
  fun rejectsProducedWorkerWithInheritedContractAndImplementation() {
  }

  @Singleton
  class Factory {
    @Produces
    @Singleton
    fun worker(): Worker = Worker()
  }

  interface Contract {
    @ProcessEngineWorker
    fun work(): Map<String, Any?>
  }

  open class Base {
    open fun work(): Map<String, Any?> = emptyMap()
  }

  class Worker : Base(), Contract
}

class ProducedWorkerTypeClosureTest {
  companion object {
    @JvmField
    @RegisterExtension
    val APP = QuarkusExtensionTest().withApplicationRoot {
      it.addClasses(
        Factory::class.java, Contract::class.java, SpecializedContract::class.java, Worker::class.java
      )
    }
      .assertException { assertThat(it).hasStackTraceContaining("Worker producer beans are unsupported") }
  }

  @Test
  fun rejectsProducerWhoseWorkerMetadataComesFromASuperinterface() {
  }

  @Singleton
  class Factory {
    @Produces
    @Singleton
    fun worker(): SpecializedContract = Worker()
  }

  interface Contract {
    @ProcessEngineWorker
    fun work(): Map<String, Any?>
  }

  interface SpecializedContract : Contract
  class Worker : SpecializedContract {
    override fun work(): Map<String, Any?> = emptyMap()
  }
}
