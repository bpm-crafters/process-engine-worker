package dev.bpmcrafters.example.quarkus

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.process.StartProcessApi
import dev.bpmcrafters.processengineapi.process.StartProcessByMessageCmd
import dev.bpmcrafters.processengineapi.process.ProcessInformation
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.deploy.DeployBundleCommand
import dev.bpmcrafters.processengineapi.deploy.DeploymentInformation
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Alternative
import jakarta.enterprise.inject.Produces
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

internal inline fun <reified T> api(noinline call: (String, Array<out Any?>) -> Any?): T =
  Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
    call(method.name, args ?: emptyArray())
  } as T

/** Test-only overrides keep HTTP tests offline even when the resource invokes an API. */
@Alternative @Priority(1) @ApplicationScoped
class TestApis {
  val starts = CopyOnWriteArrayList<StartProcessByMessageCmd>()
  val deployments = CopyOnWriteArrayList<RecordedDeployment>()

  fun reset() {
    starts.clear()
    deployments.clear()
  }

  @Produces @Alternative @Priority(1) @ApplicationScoped
  fun start(): StartProcessApi = api { method, args ->
    check(method == "startProcess") { "Unexpected StartProcessApi call: $method" }
    starts.add(args.single() as StartProcessByMessageCmd)
    CompletableFuture.completedFuture(ProcessInformation("", emptyMap()))
  }
  @Produces @Alternative @Priority(1) @ApplicationScoped
  fun deployment(): DeploymentApi = api { method, args ->
    check(method == "deploy") { "Unexpected DeploymentApi call: $method" }
    val command = args.single() as DeployBundleCommand
    deployments.add(RecordedDeployment(command.resources.map { RecordedResource(it.name, it.resourceStream.readAllBytes()) }, command.tenantId))
    CompletableFuture.completedFuture(DeploymentInformation("test-deployment", null, null))
  }
  @Produces @Alternative @Priority(1) @ApplicationScoped
  fun completion(): UserTaskCompletionApi = api { _, _ -> CompletableFuture.completedFuture(Empty) }
}

data class RecordedResource(val name: String, val bytes: ByteArray)
data class RecordedDeployment(val resources: List<RecordedResource>, val tenantId: String?)
