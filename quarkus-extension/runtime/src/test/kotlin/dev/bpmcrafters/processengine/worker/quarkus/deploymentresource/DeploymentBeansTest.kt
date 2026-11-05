package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource

import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.deploy.DeploymentInformation
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.Dependent
import jakarta.enterprise.inject.Instance
import jakarta.enterprise.inject.spi.Bean
import jakarta.inject.Singleton
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class DeploymentBeansTest {
  @Test
  fun dependentHandleIsDestroyedAfterEachSuccessfulOrFailedInvocation() {
    val api = mock<DeploymentApi>()
    whenever(api.deploy(any())).thenReturn(CompletableFuture.completedFuture(null),
      CompletableFuture.failedFuture(IllegalStateException("failed")))
    val instance = instance(api, Dependent::class.java)
    val deployment = service(instance)
    deployment.deployResources()
    assertThatThrownBy { deployment.deployResources() }.isInstanceOf(ExecutionException::class.java)
    verify(instance.handle, times(2)).destroy()
  }

  @Test
  fun dependentHandleIsDestroyedAfterTimeoutButSharedClientIsNeverDestroyed() {
    val api = mock<DeploymentApi>()
    val timeout = object : CompletableFuture<DeploymentInformation>() {
      override fun get(duration: Long, unit: TimeUnit): DeploymentInformation = throw TimeoutException("expected")
    }
    whenever(api.deploy(any())).thenReturn(timeout)
    val dependent = instance(api, Dependent::class.java)
    assertThatThrownBy { service(dependent).deployResources() }.isInstanceOf(TimeoutException::class.java)
    verify(dependent.handle).destroy()
    whenever(api.deploy(any())).thenReturn(CompletableFuture.completedFuture(null))
    for (scope in listOf(Singleton::class.java, ApplicationScoped::class.java)) {
      val shared = instance(api, scope)
      service(shared).deployResources()
      verify(shared.handle, never()).destroy()
      verify(shared.handle, never()).close()
    }
  }

  @Test
  fun missingApiDoesNotAcquireHandle() {
    val instance = mock<Instance<DeploymentApi>>()
    whenever(instance.isUnsatisfied).thenReturn(true)
    assertThat(service(instance).deployResources()).isNull()
    verify(instance, never()).handle
  }

  private fun instance(api: DeploymentApi, scope: Class<out Annotation>): Instance<DeploymentApi> {
    val instance = mock<Instance<DeploymentApi>>()
    val handle = mock<Instance.Handle<DeploymentApi>>()
    val bean = mock<Bean<DeploymentApi>>()
    whenever(instance.handle).thenReturn(handle)
    whenever(handle.get()).thenReturn(api)
    whenever(handle.bean).thenReturn(bean)
    whenever(bean.scope).thenAnswer { scope }
    return instance
  }

  private fun service(api: Instance<DeploymentApi>): ProcessDeployment {
    val resources = object : DeploymentResources(listOf("order.bpmn")) {
      override fun open(path: String): InputStream = ByteArrayInputStream(byteArrayOf(1))
    }
    val config = object : DeploymentConfig {
      override fun enabled() = false
      override fun deploymentTimeoutInSeconds() = 30L
    }
    return DeploymentBeans().processDeployment(api, resources, config)
  }
}
