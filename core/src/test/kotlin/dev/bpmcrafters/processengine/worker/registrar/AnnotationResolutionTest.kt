package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Verifies the alias resolution of the annotations and the annotation lookup without Spring's AnnotationUtils.
 */
internal class AnnotationResolutionTest {

  @Test
  fun `resolves topic from value alias`() {
    class Bean {
      @ProcessEngineWorker(value = "from-value")
      fun work() = Unit
    }
    assertThat(Bean::class.java.getAnnotatedWorkers().single().getTopic()).isEqualTo("from-value")
  }

  @Test
  fun `resolves topic from topic attribute`() {
    class Bean {
      @ProcessEngineWorker(topic = "from-topic")
      fun work() = Unit
    }
    assertThat(Bean::class.java.getAnnotatedWorkers().single().getTopic()).isEqualTo("from-topic")
  }

  @Test
  fun `resolves topic from method name if unset`() {
    class Bean {
      @ProcessEngineWorker
      fun work() = Unit
    }
    assertThat(Bean::class.java.getAnnotatedWorkers().single().getTopic()).isEqualTo("work")
  }

  @Test
  fun `accepts equal topic and value`() {
    class Bean {
      @ProcessEngineWorker(topic = "same", value = "same")
      fun work() = Unit
    }
    assertThat(Bean::class.java.getAnnotatedWorkers().single().getTopic()).isEqualTo("same")
  }

  @Test
  fun `rejects conflicting topic and value`() {
    class Bean {
      @ProcessEngineWorker(topic = "a", value = "b")
      fun work() = Unit
    }
    assertThatThrownBy { Bean::class.java.getAnnotatedWorkers().single().getTopic() }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("'topic'").hasMessageContaining("'value'")
  }

  @Test
  fun `resolves variable name from value alias and parameter name`() {
    class Bean {
      @ProcessEngineWorker
      fun work(@Variable("v") first: String, @Variable(name = "n") second: String, @Variable third: String) = Unit
    }
    val parameters = Bean::class.java.getAnnotatedWorkers().single().parameters
    assertThat(parameters[0].extractVariableName()).isEqualTo("v")
    assertThat(parameters[1].extractVariableName()).isEqualTo("n")
    assertThat(parameters[2].extractVariableName()).isEqualTo("third")
  }

  @Test
  fun `rejects conflicting variable name and value`() {
    class Bean {
      @ProcessEngineWorker
      fun work(@Variable(name = "a", value = "b") first: String) = Unit
    }
    assertThatThrownBy { Bean::class.java.getAnnotatedWorkers().single().parameters[0].extractVariableName() }
      .isInstanceOf(IllegalStateException::class.java)
  }

  abstract class AbstractWorker {
    @ProcessEngineWorker("inherited")
    abstract fun work(): Map<String, Any>
  }

  class OverridingWorker : AbstractWorker() {
    @ProcessEngineWorker("overridden")
    override fun work(): Map<String, Any> = mapOf()
  }

  @Test
  fun `reads annotation of the annotated override`() {
    val workers = OverridingWorker::class.java.getAnnotatedWorkers()
    assertThat(workers).hasSize(1)
    assertThat(workers.single().getTopic()).isEqualTo("overridden")
  }

  open class CovariantBase {
    @ProcessEngineWorker("covariant")
    open fun work(): Any = mapOf<String, Any>()
  }

  class CovariantWorker : CovariantBase() {
    @ProcessEngineWorker("covariant")
    override fun work(): Map<String, Any> = mapOf()
  }

  @Test
  fun `reads annotation through bridge methods`() {
    // the covariant override creates a bridge method; both are public methods of the class
    val methods = CovariantWorker::class.java.methods.filter { it.name == "work" }
    assertThat(methods).anyMatch { it.isBridge }
    methods.forEach { method ->
      assertThat(method.getProcessEngineWorkerAnnotation()).isNotNull
      assertThat(method.getTopic()).isEqualTo("covariant")
    }
  }

  interface WorkerContract {
    @ProcessEngineWorker("from-interface")
    fun work(): Map<String, Any>
  }

  @Test
  fun `finds annotation declared on interface method when reading attributes`() {
    class Impl : WorkerContract {
      override fun work(): Map<String, Any> = mapOf()
    }
    // discovery requires the annotation on the method itself (unchanged behaviour) ...
    assertThat(Impl::class.java.getAnnotatedWorkers()).isEmpty()
    // ... but attribute reading falls back to the type hierarchy
    assertThat(Impl::class.java.getMethod("work").getTopic()).isEqualTo("from-interface")
  }

  class ClassConverter : VariableConverter {
    override fun <T : Any> mapToType(value: Any?, type: Class<T>): T = type.cast("class:$value")
  }

  object ObjectConverter : VariableConverter {
    override fun <T : Any> mapToType(value: Any?, type: Class<T>): T = type.cast("object:$value")
  }

  @Test
  fun `instantiates converters with no-arg constructor and kotlin objects`() {
    class Bean {
      @ProcessEngineWorker
      fun work(@Variable(converter = ClassConverter::class) a: String, @Variable(converter = ObjectConverter::class) b: String) = Unit
    }
    val parameters = Bean::class.java.getAnnotatedWorkers().single().parameters
    val default = Variable.DefaultVariableConverter
    assertThat(parameters[0].extractVariableConverter(default).mapToType("x", String::class.java)).isEqualTo("class:x")
    assertThat(parameters[1].extractVariableConverter(default)).isSameAs(ObjectConverter)
  }

  open class UserClass {
    @ProcessEngineWorker("user")
    open fun work() = Unit
  }

  @Test
  fun `unwraps generated subclasses by naming convention`() {
    // simulate a CGLIB-style generated subclass name using a dynamically named class is not possible here,
    // so verify the user class is returned unchanged and synthetic detection does not break plain classes
    assertThat(UserClass::class.java.unwrapGeneratedSubclass()).isSameAs(UserClass::class.java)
    assertThat(UserClass().getAnnotatedWorkers().single().getTopic()).isEqualTo("user")
  }
}
