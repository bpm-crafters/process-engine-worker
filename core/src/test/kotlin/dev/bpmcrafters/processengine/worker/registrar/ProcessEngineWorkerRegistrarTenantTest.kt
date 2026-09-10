package dev.bpmcrafters.processengine.worker.registrar

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.configuration.ProcessEngineWorkerConfiguration
import dev.bpmcrafters.processengine.worker.transaction.TransactionalExecutor
import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.task.ServiceTaskCompletionApi
import dev.bpmcrafters.processengineapi.task.SubscribeForTaskCmd
import dev.bpmcrafters.processengineapi.task.TaskSubscriptionApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.concurrent.CompletableFuture

internal class ProcessEngineWorkerRegistrarTenantTest {

  private val taskSubscriptionApi = mock<TaskSubscriptionApi>()
  private val taskCompletionApi = mock<ServiceTaskCompletionApi>()
  private val variableConverter = mock<VariableConverter>()
  private val parameterResolver = mock<ParameterResolver>()
  private val resultResolver = mock<ResultResolver>()
  private val metrics = mock<ProcessEngineWorkerMetrics>()
  private val idempotencyRegistry = mock<IdempotencyRegistry>()

  private fun registrar(configuration: ProcessEngineWorkerConfiguration = ProcessEngineWorkerConfiguration()) = ProcessEngineWorkerRegistrar(
    configuration,
    taskSubscriptionApi,
    taskCompletionApi,
    variableConverter,
    parameterResolver,
    resultResolver,
    TransactionalExecutor.NONE,
    metrics,
    idempotencyRegistry
  )

  @Test
  fun `should use tenantId from annotation`() {
    class TenantWorker {
      @ProcessEngineWorker(topic = "test", tenantId = "tenant-from-annotation")
      fun work() {}
    }

    val bean = TenantWorker()
    whenever(taskSubscriptionApi.subscribeForTask(any())).thenReturn(CompletableFuture.completedFuture(mock()))

    registrar().registerWorkers(bean, beanName = "tenantWorker")

    val captor = argumentCaptor<SubscribeForTaskCmd>()
    verify(taskSubscriptionApi).subscribeForTask(captor.capture())

    assertThat(captor.firstValue.restrictions[CommonRestrictions.TENANT_ID]).isEqualTo("tenant-from-annotation")
  }

  @Test
  fun `should use tenantId from properties if not specified on annotation`() {
    class NoTenantWorker {
      @ProcessEngineWorker(topic = "test")
      fun work() {}
    }

    val bean = NoTenantWorker()
    whenever(taskSubscriptionApi.subscribeForTask(any())).thenReturn(CompletableFuture.completedFuture(mock()))

    registrar(ProcessEngineWorkerConfiguration(tenantId = "tenant-from-properties")).registerWorkers(bean, beanName = "noTenantWorker")

    val captor = argumentCaptor<SubscribeForTaskCmd>()
    verify(taskSubscriptionApi).subscribeForTask(captor.capture())

    assertThat(captor.firstValue.restrictions[CommonRestrictions.TENANT_ID]).isEqualTo("tenant-from-properties")
  }

  @Test
  fun `annotation tenantId should overrule property tenantId`() {
    class OverruleWorker {
      @ProcessEngineWorker(topic = "test", tenantId = "tenant-from-annotation")
      fun work() {}
    }

    val bean = OverruleWorker()
    whenever(taskSubscriptionApi.subscribeForTask(any())).thenReturn(CompletableFuture.completedFuture(mock()))

    registrar(ProcessEngineWorkerConfiguration(tenantId = "tenant-from-properties")).registerWorkers(bean, beanName = "overruleWorker")

    val captor = argumentCaptor<SubscribeForTaskCmd>()
    verify(taskSubscriptionApi).subscribeForTask(captor.capture())

    assertThat(captor.firstValue.restrictions[CommonRestrictions.TENANT_ID]).isEqualTo("tenant-from-annotation")
  }

  @Test
  fun `should not have tenantId restriction if neither is specified`() {
    class NoTenantAtAllWorker {
      @ProcessEngineWorker(topic = "test")
      fun work() {}
    }

    val bean = NoTenantAtAllWorker()
    whenever(taskSubscriptionApi.subscribeForTask(any())).thenReturn(CompletableFuture.completedFuture(mock()))

    registrar(ProcessEngineWorkerConfiguration(tenantId = null)).registerWorkers(bean, beanName = "noTenantAtAllWorker")

    val captor = argumentCaptor<SubscribeForTaskCmd>()
    verify(taskSubscriptionApi).subscribeForTask(captor.capture())

    assertThat(captor.firstValue.restrictions).doesNotContainKey(CommonRestrictions.TENANT_ID)
  }
}
