package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.aop.framework.ProxyFactory
import org.springframework.transaction.annotation.Transactional

class ReflectionFacadeSourceTest {
  @Test
  fun `legacy discovery handles Spring proxies and synthetic Kotlin helpers without extension ambiguity`() {
    val factory = ProxyFactory(DefaultWorker())
    factory.isProxyTargetClass = true
    val methods = legacyGetAnnotatedWorkers(factory.proxy)
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
}
