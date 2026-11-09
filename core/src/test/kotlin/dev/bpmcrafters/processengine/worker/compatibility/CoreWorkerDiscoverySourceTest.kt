package dev.bpmcrafters.processengine.worker.compatibility

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.registrar.findAnnotatedWorkerMethods
import dev.bpmcrafters.processengine.worker.registrar.getTopic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CoreWorkerDiscoverySourceTest {
  @Test
  fun `neutral discovery inspects an ordinary bean through Any with only core on the classpath`() {
    val bean: Any = Worker()

    val methods = bean.findAnnotatedWorkerMethods()

    assertThat(methods).containsExactly(Worker::class.java.getMethod("work"))
    assertThat(methods.map { it.getTopic() }).containsExactly("core-topic")
  }

  @Test
  fun `neutral discovery supports bound and unbound callable references through Any`() {
    val bean: Any = Worker()
    val bound = bean::findAnnotatedWorkerMethods
    val unbound = Any::findAnnotatedWorkerMethods
    val expected = Worker::class.java.getMethod("work")

    assertThat(bound()).containsExactly(expected)
    assertThat(unbound(bean)).containsExactly(expected)
  }

  @Test
  fun `neutral discovery supports concrete receivers and their callable references`() {
    val bean = Worker()
    val bound = bean::findAnnotatedWorkerMethods
    val unbound = Worker::findAnnotatedWorkerMethods
    val expected = Worker::class.java.getMethod("work")

    assertThat(bean.findAnnotatedWorkerMethods()).containsExactly(expected)
    assertThat(bound()).containsExactly(expected)
    assertThat(unbound(bean)).containsExactly(expected)
  }

  class Worker {
    @ProcessEngineWorker("core-topic")
    fun work() {}

    fun unrelated() {}
  }
}
