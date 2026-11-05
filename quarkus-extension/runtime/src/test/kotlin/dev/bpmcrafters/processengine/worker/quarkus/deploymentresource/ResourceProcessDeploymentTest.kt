package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource

import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.deploy.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class ResourceProcessDeploymentTest {
  private val api = TestApi()
  private val resources = TestResources()

  @Test
  fun catalogIsAnImmutableSnapshotAvailableAtConstruction() {
    val paths = mutableListOf("order.bpmn")
    val catalog = DeploymentResources(paths)
    paths.clear()
    assertThat(catalog.paths()).containsExactly("order.bpmn")
    assertThatThrownBy { (catalog.paths() as MutableList<String>).clear() }
      .isInstanceOf(UnsupportedOperationException::class.java)
  }

  @Test
  fun manualDeploymentBundlesBothTypesAndClosesOwnedStreamsBeforeApiInvocation() {
    resources.configure(listOf("generated/0/order.bpmn", "generated/1/rules.dmn"))
    val result = ResourceProcessDeployment(api, resources, 30).deployResources()
    assertThat(result).isSameAs(api.information)
    assertThat(api.command!!.resources.map { it.name }).containsExactly("order.bpmn", "rules.dmn")
    assertThat(api.command!!.resources).allSatisfy { assertThat(it.resourceHeaders).isEmpty() }
    assertThat(resources.streams).allMatch { it.closed }
  }

  @Test
  fun emptyBundleDoesNotCallApi() {
    assertThat(ResourceProcessDeployment(api, resources, 30).deployResources()).isNull()
    assertThat(api.command).isNull()
  }

  @Test
  fun failurePropagatesAndClosesAllOpenedStreams() {
    resources.configure(listOf("order.bpmn"))
    val failure = IllegalStateException("deployment failed")
    api.future = CompletableFuture.failedFuture(failure)
    assertThatThrownBy { ResourceProcessDeployment(api, resources, 30).deployResources() }
      .isInstanceOf(ExecutionException::class.java).hasCause(failure)
    assertThat(resources.streams).allMatch { it.closed }
  }

  @Test
  fun boundedTimeoutPropagatesWithoutWaitingInTest() {
    resources.configure(listOf("order.bpmn"))
    api.future = object : CompletableFuture<DeploymentInformation>() {
      override fun get(timeout: Long, unit: TimeUnit): DeploymentInformation {
        assertThat(timeout).isEqualTo(7L)
        assertThat(unit).isEqualTo(TimeUnit.SECONDS)
        throw TimeoutException("expected")
      }
    }
    assertThatThrownBy { ResourceProcessDeployment(api, resources, 7).deployResources() }
      .isInstanceOf(TimeoutException::class.java)
    assertThat(api.future).isCancelled()
    assertThat(resources.streams).allMatch { it.closed }
  }

  @Test
  fun interruptionCancelsFutureRestoresFlagAndClosesStreams() {
    resources.configure(listOf("order.bpmn"))
    api.future = object : CompletableFuture<DeploymentInformation>() {
      override fun get(timeout: Long, unit: TimeUnit): DeploymentInformation = throw InterruptedException("expected")
    }
    try {
      assertThatThrownBy { ResourceProcessDeployment(api, resources, 30).deployResources() }
        .isInstanceOf(InterruptedException::class.java)
      assertThat(Thread.currentThread().isInterrupted).isTrue()
      assertThat(api.future).isCancelled()
      assertThat(resources.streams).allMatch { it.closed }
    } finally {
      Thread.interrupted()
    }
  }

  @Test
  fun partialOpenFailureClosesEarlierResources() {
    resources.configure(listOf("order.bpmn", "missing.dmn"))
    resources.failOn = "missing.dmn"
    assertThatThrownBy { ResourceProcessDeployment(api, resources, 30).deployResources() }
      .isInstanceOf(IOException::class.java).hasMessage("missing")
    assertThat(resources.streams).hasSize(1).allMatch { it.closed }
    assertThat(api.command).isNull()
  }

  @Test
  fun lateConsumerCanReadIndependentPayloadAfterTimeoutDespiteOwnedStreamClosure() {
    resources.configure(listOf("order.bpmn"))
    api.future = object : CompletableFuture<DeploymentInformation>() {
      override fun get(timeout: Long, unit: TimeUnit): DeploymentInformation = throw TimeoutException("expected")
    }
    assertThatThrownBy { ResourceProcessDeployment(api, resources, 30).deployResources() }
      .isInstanceOf(TimeoutException::class.java)
    assertThat(resources.streams).allMatch { it.closed }
    assertThat(api.command!!.resources[0].resourceStream.readAllBytes()).containsExactly(1.toByte())
  }

  @Test
  fun nonpositiveTimeoutIsRejected() {
    assertThatThrownBy { ResourceProcessDeployment(api, resources, 0) }.isInstanceOf(IllegalArgumentException::class.java)
  }

  private class TestResources : DeploymentResources(emptyList()) {
    val streams = mutableListOf<TrackedStream>()
    var failOn: String? = null
    private var paths = emptyList<String>()

    fun configure(paths: List<String>) { this.paths = java.util.List.copyOf(paths) }
    override fun paths(): List<String> = paths

    override fun open(path: String): InputStream {
      if (path == failOn) throw IOException("missing")
      return TrackedStream().also(streams::add)
    }
  }

  private class TrackedStream : ByteArrayInputStream(byteArrayOf(1)) {
    var closed = false
    override fun close() { closed = true }
    override fun readAllBytes(): ByteArray {
      check(!closed) { "owned stream already closed" }
      return super.readAllBytes()
    }
  }

  private inner class TestApi : DeploymentApi {
    val information = DeploymentInformation("deployment", Instant.EPOCH, null)
    var future: CompletableFuture<DeploymentInformation> = CompletableFuture.completedFuture(information)
    var command: DeployBundleCommand? = null

    override fun deploy(command: DeployBundleCommand): CompletableFuture<DeploymentInformation> {
      this.command = command
      assertThat(resources.streams).allMatch { it.closed }
      command.resources.forEach { assertThat(it.resourceStream).isExactlyInstanceOf(ByteArrayInputStream::class.java) }
      return future
    }

    override fun meta(instance: MetaInfoAware): MetaInfo = object : MetaInfo {}
  }
}
