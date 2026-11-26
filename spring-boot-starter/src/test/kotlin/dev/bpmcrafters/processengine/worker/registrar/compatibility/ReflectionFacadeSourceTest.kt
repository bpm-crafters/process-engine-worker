package dev.bpmcrafters.processengine.worker.registrar.compatibility

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import dev.bpmcrafters.processengine.worker.registrar.getAnnotatedWorkers
import dev.bpmcrafters.processengine.worker.registrar.getTopic
import dev.bpmcrafters.processengine.worker.registrar.isTransactional
import dev.bpmcrafters.processengine.worker.registrar.legacyWorkerMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.aop.framework.ProxyFactory
import org.springframework.transaction.annotation.Transactional
import java.lang.reflect.Proxy

class ReflectionFacadeSourceTest {
  @Test
  fun `Kotlin discovery finds implementation annotations on a JDK proxy`() {
    val proxy = ProxyFactory(InterfaceWorker()).proxy

    assertThat(Proxy.isProxyClass(proxy.javaClass)).isTrue()
    assertThat(proxy.getAnnotatedWorkers()).containsExactly(
      InterfaceWorker::class.java.getMethod("work", String::class.java)
    )
  }

  @Test
  fun `Kotlin discovery handles class proxies and ignores synthetic helpers`() {
    val factory = ProxyFactory(DefaultWorker())
    factory.isProxyTargetClass = true
    val methods = factory.proxy.getAnnotatedWorkers()
    assertThat(methods.map { it.name }).containsExactly("work")
    val method = methods.single()
    assertThat(legacyWorkerMetadata(method, DefaultWorker::class.java)).isEqualTo(method)
    assertThat(method.isTransactional()).isTrue()
    assertThat(method.isTransactional(DefaultWorker::class.java)).isTrue()
    assertThat(method.getTopic()).isEqualTo("work")
  }

  open class DefaultWorker {
    @ProcessEngineWorker @Transactional
    open fun work(@Variable("value") value: String = "default"): Map<String, Any> = mapOf("value" to value)
  }

  interface WorkerContract {
    fun work(value: String)
  }

  class InterfaceWorker : WorkerContract {
    @ProcessEngineWorker
    override fun work(value: String) = Unit
  }
}
