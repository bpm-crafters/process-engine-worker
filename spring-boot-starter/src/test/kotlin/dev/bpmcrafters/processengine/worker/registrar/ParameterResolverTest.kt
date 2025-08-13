package dev.bpmcrafters.processengine.worker.registrar

import com.fasterxml.jackson.databind.ObjectMapper
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import java.lang.reflect.InvocationTargetException
import java.util.*


/**
 * Test making sure parameter resolver works as expected and the resulting arguments array can be used
 * for reflective invocation of the worker method.
 */
@Suppress("UNUSED_PARAMETER")
class ParameterResolverTest {

  private val resolver = ParameterResolver.builder().build()
  private val taskInformation = TaskInformation(taskId = UUID.randomUUID().toString(), meta = mapOf())
  private val payload: MutableMap<String, Any> = mutableMapOf()
  private val variableConverter = JacksonVariableConverter(ObjectMapper())
  private val taskCompletionApi = mock<ServiceTaskCompletionApi>()

  @Test
  fun `works with methods without arguments`() {
    class Worker {
      @ProcessEngineWorker
      fun work() {
      }
    }

    val worker = Worker()
    val method = worker.getAnnotatedWorkers().first()
    val args = resolver.createInvocationArguments(method, taskInformation, payload, variableConverter, taskCompletionApi)
    assertThat(args).isEmpty()
    method.invoke(worker, *args)
  }

  @Test
  fun `detect task information only`() {

    class Worker {
      @ProcessEngineWorker
      fun work(ti: TaskInformation) {
      }
    }

    val worker = Worker()
    val method = worker.getAnnotatedWorkers().first()
    val args = resolver.createInvocationArguments(method, taskInformation, payload, variableConverter, taskCompletionApi)
    assertThat(args).hasSize(1)
    assertThat(args[0]).isEqualTo(taskInformation)
    method.invoke(worker, *args)
  }


  @Test
  fun `detect variable converter only`() {

    class Worker {
      @ProcessEngineWorker
      fun work(vc: JacksonVariableConverter) {
      }
    }

    val worker = Worker()
    val method = worker.getAnnotatedWorkers().first()
    val args = resolver.createInvocationArguments(method, taskInformation, payload, variableConverter, taskCompletionApi)
    assertThat(args).hasSize(1)
    assertThat(args[0]).isEqualTo(variableConverter)
    method.invoke(worker, *args)
  }

  @Test
  fun `detect task information and payload and task completion api`() {

    class Worker {
      @ProcessEngineWorker
      fun work_A(payload: Map<String, Any>, ti: TaskInformation, api: ServiceTaskCompletionApi) {
      }

      @ProcessEngineWorker
      fun work_B(ti: TaskInformation, payload: Map<String, Any>, api: ServiceTaskCompletionApi) {
      }

      @ProcessEngineWorker
      fun work_C(api: ServiceTaskCompletionApi, ti: TaskInformation, payload: Map<String, Any>) {
      }
    }

    val worker = Worker()
    val workers = worker.getAnnotatedWorkers()

    val work = workers.first { it.name == "work_A" }
    val args = resolver.createInvocationArguments(work, taskInformation, payload, variableConverter, taskCompletionApi)
    assertThat(args).isNotNull
    assertThat(args).hasSize(3)
    assertThat(args[0]).isEqualTo(payload)
    assertThat(args[1]).isEqualTo(taskInformation)
    assertThat(args[2]).isEqualTo(taskCompletionApi)
    work.invoke(worker, *args)

    val work2 = workers.first { it.name == "work_B" }
    val args2 = resolver.createInvocationArguments(work2, taskInformation, payload, variableConverter, taskCompletionApi)
    assertThat(args2).isNotNull
    assertThat(args2).hasSize(3)
    assertThat(args2[0]).isEqualTo(taskInformation)
    assertThat(args2[1]).isEqualTo(payload)
    assertThat(args2[2]).isEqualTo(taskCompletionApi)
    work2.invoke(worker, *args2)

    val work3 = workers.first { it.name == "work_C" }
    val args3 = resolver.createInvocationArguments(work3, taskInformation, payload, variableConverter, taskCompletionApi)
    assertThat(args3).isNotNull
    assertThat(args3).hasSize(3)
    assertThat(args3[0]).isEqualTo(taskCompletionApi)
    assertThat(args3[1]).isEqualTo(taskInformation)
    assertThat(args3[2]).isEqualTo(payload)
    work3.invoke(worker, *args3)
  }

  @Test
  fun `detect annotated variables`() {

    class Worker {
      @ProcessEngineWorker
      fun work(@Variable("string") var1: String, @Variable("long") var2: Long) {
      }
    }

    payload["string"] = "foo"
    payload["long"] = 17L

    val worker = Worker()
    val method = worker.getAnnotatedWorkers().first()
    val args = resolver.createInvocationArguments(method, taskInformation, payload, variableConverter, taskCompletionApi)
    assertThat(args).isNotNull
    assertThat(args).hasSize(2)
    assertThat(args[0]).isEqualTo("foo")
    assertThat(args[1]).isEqualTo(17L)
    method.invoke(worker, *args)
  }

  @Test
  fun `detect annotated non-mandatory variable`() {

    class Worker {
      @ProcessEngineWorker
      fun work(@Variable(name = "string", mandatory = false) var1: String?) {
      }
    }

    payload["long"] = 17L

    val worker = Worker()
    val method = worker.getAnnotatedWorkers().first()
    val args = resolver.createInvocationArguments(method, taskInformation, payload, variableConverter, taskCompletionApi)
    assertThat(args).isNotNull
    assertThat(args).hasSize(1)
    assertThat(args[0]).isNull()
    method.invoke(worker, *args)
  }

  @Test
  fun `detect annotated non-mandatory optional variable`() {

    class Worker {
      @ProcessEngineWorker
      fun work(@Variable(name = "string") var1: Optional<String>) {
      }
    }

    payload["long"] = 17L

    val worker = Worker()
    val method = worker.getAnnotatedWorkers().first()
    val args = resolver.createInvocationArguments(method, taskInformation, payload, variableConverter, taskCompletionApi)
    assertThat(args).isNotNull
    assertThat(args).hasSize(1)
    assertThat(args[0]).isInstanceOf(Optional::class.java)
    assertThat(args[0] as Optional<*>).isEmpty
    method.invoke(worker, *args)
  }

  @Test
  fun `detect annotated mandatory variable with custom converter`() {

    class Worker {
      @ProcessEngineWorker
      fun work(@Variable(name = "string", converter = MyEnum.Converter::class) var1: MyEnum) {
      }
    }

    payload["string"] = "TWO"

    val worker = Worker()
    val method = worker.getAnnotatedWorkers().first()
    val args = resolver.createInvocationArguments(method, taskInformation, payload, variableConverter, taskCompletionApi)
    assertThat(args).isNotNull
    assertThat(args).hasSize(1)
    assertThat(args[0]).isInstanceOf(MyEnum::class.java)
    assertThat(args[0] as MyEnum).isEqualTo(MyEnum.TWO)
    method.invoke(worker, *args)
  }


  @Test
  fun `fail to invoke annotated non-mandatory variable`() {

    class Worker {
      @ProcessEngineWorker
      fun work(@Variable(name = "string", mandatory = false) var1: String) { // not null is required
      }
    }

    payload["long"] = 17L

    val worker = Worker()
    val method = worker.getAnnotatedWorkers().first()
    val args = resolver.createInvocationArguments(method, taskInformation, payload, variableConverter, taskCompletionApi)
    assertThat(args).isNotNull
    assertThat(args).hasSize(1)
    assertThat(args[0]).isNull()
    assertThrows<InvocationTargetException> {
      method.invoke(worker, *args)
    }

  }

  @Test
  fun `fail if annotated variable does not exist`() {

    class Worker {
      @ProcessEngineWorker
      fun work(@Variable("not-existing") var1: String, @Variable("long") var2: Long) {
      }
    }

    payload["string"] = "foo"
    payload["long"] = 17L

    val worker = Worker()
    val method = worker.getAnnotatedWorkers().first()
    val exception =
      assertThrows<IllegalArgumentException> { resolver.createInvocationArguments(method, taskInformation, payload, variableConverter, taskCompletionApi) }
    assertThat(exception).hasMessage("Expected payload to contain variable 'not-existing', but it contained 'string', 'long'.")

  }

  @Test
  fun `fail if non-resolvable argument is present`() {

    class Worker {
      @ProcessEngineWorker
      fun work(taskInformation: TaskInformation, nonResolvable: String) {
      }
    }

    val worker = Worker()
    val method = worker.getAnnotatedWorkers().first()
    val exception =
      assertThrows<IllegalArgumentException> { resolver.createInvocationArguments(method, taskInformation, payload, variableConverter, taskCompletionApi) }
    assertThat(exception).hasMessage("Found a method with some unsupported parameters annotated with `@ProcessEngineWorker`. Could not find a strategy to resolve argument 1 of Worker#work of type String.")

  }
}
