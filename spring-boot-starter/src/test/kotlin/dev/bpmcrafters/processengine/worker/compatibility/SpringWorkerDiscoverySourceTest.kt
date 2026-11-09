package dev.bpmcrafters.processengine.worker.compatibility

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.registrar.getAnnotatedWorkers
import dev.bpmcrafters.processengine.worker.registrar.getTopic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.aop.framework.ProxyFactory
import org.springframework.aop.support.AopUtils

class SpringWorkerDiscoverySourceTest {
  @Test
  fun `original extension discovers an ordinary bean through Any`() {
    val bean: Any = ImplementationWorker()

    val methods = bean.getAnnotatedWorkers()

    assertThat(methods).containsExactly(ImplementationWorker::class.java.getMethod("work"))
    assertThat(methods.map { it.getTopic() }).containsExactly("implementation-topic")
  }

  @Test
  fun `original bound and unbound callable references retain proxy-aware no-argument discovery`() {
    val bean: Any = proxy(ImplementationWorker(), false)
    val bound = bean::getAnnotatedWorkers
    val unbound = Any::getAnnotatedWorkers
    val expected = ImplementationWorker::class.java.getMethod("work")

    assertThat(bound()).containsExactly(expected)
    assertThat(unbound(bean)).containsExactly(expected)
  }

  @Test
  fun `interface-typed JDK proxy calls and references prefer Spring discovery`() {
    val bean = proxy(ImplementationWorker(), false) as UnannotatedContract
    val bound = bean::getAnnotatedWorkers
    val unbound = UnannotatedContract::getAnnotatedWorkers
    val expected = ImplementationWorker::class.java.getMethod("work")

    assertThat(bean.getAnnotatedWorkers()).containsExactly(expected)
    assertThat(bound()).containsExactly(expected)
    assertThat(unbound(bean)).containsExactly(expected)
  }

  @Test
  fun `concrete-typed CGLIB proxy calls and references prefer Spring discovery`() {
    val bean = proxy(ImplementationWorker(), true) as ImplementationWorker
    val bound = bean::getAnnotatedWorkers
    val unbound = ImplementationWorker::getAnnotatedWorkers
    val expected = ImplementationWorker::class.java.getMethod("work")

    assertThat(bean.getAnnotatedWorkers()).containsExactly(expected)
    assertThat(bound()).containsExactly(expected)
    assertThat(unbound(bean)).containsExactly(expected)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `original extension discovers implementation-only metadata through Spring proxy typed Any`(classProxy: Boolean) {
    val bean: Any = proxy(ImplementationWorker(), classProxy)

    val methods = bean.getAnnotatedWorkers()

    assertThat(methods).containsExactly(ImplementationWorker::class.java.getMethod("work"))
    assertThat(methods.map { it.getTopic() }).containsExactly("implementation-topic")
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `original extension retains interface metadata on target implementation`(classProxy: Boolean) {
    val bean: Any = proxy(InterfaceWorker(), classProxy)

    val methods = bean.getAnnotatedWorkers()

    assertThat(methods).containsExactly(InterfaceWorker::class.java.getMethod("work"))
    assertThat(methods.map { it.getTopic() }).containsExactly("interface-topic")
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `original extension resolves generic metadata once without bridge or unrelated overload`(classProxy: Boolean) {
    val bean: Any = proxy(GenericWorker(), classProxy)

    val methods = bean.getAnnotatedWorkers()

    assertThat(methods).containsExactly(GenericWorker::class.java.getMethod("work", String::class.java))
    assertThat(methods.map { it.getTopic() }).containsExactly("generic-topic")
    assertThat(methods.single().isBridge).isFalse()
    assertThat(methods.single().isSynthetic).isFalse()
  }

  private fun proxy(target: Any, classProxy: Boolean): Any {
    val factory = ProxyFactory(target)
    factory.isProxyTargetClass = classProxy
    return factory.proxy.also {
      assertThat(AopUtils.isJdkDynamicProxy(it)).isEqualTo(!classProxy)
      assertThat(AopUtils.isCglibProxy(it)).isEqualTo(classProxy)
    }
  }

  interface UnannotatedContract {
    fun work()
  }

  open class ImplementationWorker : UnannotatedContract {
    @ProcessEngineWorker("implementation-topic")
    override fun work() {}
  }

  interface AnnotatedContract {
    @ProcessEngineWorker("interface-topic")
    fun work()
  }

  open class InterfaceWorker : AnnotatedContract {
    override fun work() {}
  }

  interface GenericContract<T> {
    @ProcessEngineWorker("generic-topic")
    fun work(value: T)
  }

  open class GenericWorker : GenericContract<String> {
    override fun work(value: String) {}
    open fun work(value: Int) { error("Unannotated overload must not be discovered") }
  }
}
