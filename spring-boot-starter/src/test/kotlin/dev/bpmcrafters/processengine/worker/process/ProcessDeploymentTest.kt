package dev.bpmcrafters.processengine.worker.process

import dev.bpmcrafters.processengineapi.deploy.DeployBundleCommand
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.deploy.DeploymentInformation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatNoException
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.*
import org.springframework.core.io.Resource
import org.springframework.core.io.support.ResourcePatternResolver
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.CompletableFuture

class ProcessDeploymentTest {

  private val resourcePatternResolver: ResourcePatternResolver = mock<ResourcePatternResolver>()
  private val deploymentApi: DeploymentApi = mock<DeploymentApi>()
  private val properties: ProcessEngineWorkerDeploymentProperties = ProcessEngineWorkerDeploymentProperties()
  private val processDeployment: ProcessDeployment = ProcessDeployment(resourcePatternResolver, deploymentApi, properties)

  @Test
  fun `should deploy all resources successfully`() {
    val bpmnResource = mock<Resource>()
    val dmnResource = mock<Resource>()
    whenever(bpmnResource.filename).thenReturn("process.bpmn")
    whenever(dmnResource.filename).thenReturn("decision.dmn")
    whenever(bpmnResource.inputStream).thenReturn(ByteArrayInputStream("bpmn".toByteArray()))
    whenever(dmnResource.inputStream).thenReturn(ByteArrayInputStream("dmn".toByteArray()))
    whenever(resourcePatternResolver.getResources("classpath*:/**/*.bpmn"))
        .thenReturn(arrayOf(bpmnResource))
    whenever(resourcePatternResolver.getResources("classpath*:/**/*.dmn"))
        .thenReturn(arrayOf(dmnResource))
    whenever(deploymentApi.deploy(any<DeployBundleCommand>()))
        .thenReturn(CompletableFuture.completedFuture(DeploymentInformation("DeploymentKey", null, null)))

    processDeployment.deployResources()

    val captor = argumentCaptor<DeployBundleCommand>()
    verify(deploymentApi, times(2)).deploy(captor.capture())
    val deployedFilenames = captor.allValues.flatMap { it.resources }.map { it.name }
    assertThat(deployedFilenames).containsExactlyInAnyOrder("process.bpmn", "decision.dmn")
  }

  @Test
  fun `should handle IoException during resource loading`() {
    whenever(resourcePatternResolver.getResources(anyString()))
        .thenThrow(IOException("Resource loading failed"))

    assertThatNoException().isThrownBy {
      processDeployment.deployResources()
    }
    verify(deploymentApi, never()).deploy(any<DeployBundleCommand>())
  }

  @Test
  fun `should handle deployment failure gracefully`() {
    val bpmnResource = mock<Resource>()
    whenever(bpmnResource.filename).thenReturn("process.bpmn")
    whenever(bpmnResource.inputStream).thenReturn(ByteArrayInputStream("bpmn".toByteArray()))
    whenever(resourcePatternResolver.getResources("classpath*:/**/*.bpmn"))
      .thenReturn(arrayOf(bpmnResource))
    whenever(resourcePatternResolver.getResources("classpath*:/**/*.dmn"))
      .thenReturn(arrayOf())

    whenever(deploymentApi.deploy(any<DeployBundleCommand>()))
      .thenReturn(CompletableFuture.failedFuture(RuntimeException("Deployment failed")))

    assertThatNoException().isThrownBy {
      processDeployment.deployResources()
    }

  }

}
