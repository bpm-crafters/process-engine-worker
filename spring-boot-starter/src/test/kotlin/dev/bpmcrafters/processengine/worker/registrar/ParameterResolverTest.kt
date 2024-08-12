package dev.bpmcrafters.processengine.worker.registrar

import com.fasterxml.jackson.databind.ObjectMapper
import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import dev.bpmcrafters.processengine.worker.converter.VariableConverter
import dev.bpmcrafters.processengineapi.task.ExternalTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.TaskInformation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import java.util.*


class ParameterResolverTest {

  private val resolver = ParameterResolver.builder().build()
  private val taskInformation = TaskInformation(taskId = UUID.randomUUID().toString(), meta = mapOf())
  private val payload: MutableMap<String, Any> = mutableMapOf()
  private val variableConverter = VariableConverter(ObjectMapper())
  private val taskCompletionApi = mock<ExternalTaskCompletionApi>()

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
    assertThat(args).isNull()
  }

  @Test
  fun `detect task information only `() {

    class Worker {
      @ProcessEngineWorker
      fun work(ti: TaskInformation) {
      }
    }

    val worker = Worker()
    val method = worker.getAnnotatedWorkers().first()
    val args = resolver.createInvocationArguments(method, taskInformation, payload, variableConverter, taskCompletionApi)
    assertThat(args).isNotNull
    assertThat(args).hasSize(1)
    assertThat(args?.get(0)).isEqualTo(taskInformation)

  }

  @Test
  fun `detect task information and payload and task completion api`() {

    class Worker {
      @ProcessEngineWorker
      fun work(payload: Map<String, Any>, ti: TaskInformation, api: ExternalTaskCompletionApi) {
      }

      @ProcessEngineWorker
      fun work2(ti: TaskInformation, payload: Map<String, Any>, api: ExternalTaskCompletionApi) {
      }

      @ProcessEngineWorker
      fun work3(api: ExternalTaskCompletionApi, ti: TaskInformation, payload: Map<String, Any>) {
      }
    }

    val worker = Worker()

    val work = worker.getAnnotatedWorkers()[0]
    val args = resolver.createInvocationArguments(work, taskInformation, payload, variableConverter, taskCompletionApi)
    assertThat(args).isNotNull
    assertThat(args).hasSize(3)
    assertThat(args?.get(0)).isEqualTo(payload)
    assertThat(args?.get(1)).isEqualTo(taskInformation)
    assertThat(args?.get(2)).isEqualTo(taskCompletionApi)

    val work2 = worker.getAnnotatedWorkers()[1]
    val args2 = resolver.createInvocationArguments(work2, taskInformation, payload, variableConverter, taskCompletionApi)
    assertThat(args2).isNotNull
    assertThat(args2).hasSize(3)
    assertThat(args2?.get(0)).isEqualTo(taskInformation)
    assertThat(args2?.get(1)).isEqualTo(payload)
    assertThat(args2?.get(2)).isEqualTo(taskCompletionApi)

    val work3 = worker.getAnnotatedWorkers()[2]
    val args3 = resolver.createInvocationArguments(work3, taskInformation, payload, variableConverter, taskCompletionApi)
    assertThat(args3).isNotNull
    assertThat(args3).hasSize(3)
    assertThat(args3?.get(0)).isEqualTo(taskCompletionApi)
    assertThat(args3?.get(1)).isEqualTo(taskInformation)
    assertThat(args3?.get(2)).isEqualTo(payload)

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
    assertThat(args?.get(0)).isEqualTo("foo")
    assertThat(args?.get(1)).isEqualTo(17L)
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
    val exception = assertThrows<IllegalArgumentException> { resolver.createInvocationArguments(method, taskInformation, payload, variableConverter, taskCompletionApi) }
    assertThat(exception).hasMessage("Expected payload to contain variable 'not-existing', but it contained 'string', 'long' only.")

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
    val exception = assertThrows<IllegalArgumentException> { resolver.createInvocationArguments(method, taskInformation, payload, variableConverter, taskCompletionApi) }
    assertThat(exception).hasMessage("Found a method with some unsupported parameters annotated with `@ProcessEngineWorker`. Could not find a strategy to resolve argument 1 of Worker#work of type String.")

  }

}
