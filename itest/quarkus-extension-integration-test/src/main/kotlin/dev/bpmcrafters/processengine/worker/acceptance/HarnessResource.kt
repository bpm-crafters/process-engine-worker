package dev.bpmcrafters.processengine.worker.acceptance

import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.quarkus.deploymentresource.ProcessDeployment
import dev.bpmcrafters.processengine.worker.quarkus.WorkerConfig
import dev.bpmcrafters.processengineapi.CommonRestrictions
import dev.bpmcrafters.processengineapi.task.TaskInformation
import io.micrometer.core.instrument.MeterRegistry
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.narayana.jta.QuarkusTransactionException
import io.smallrye.common.annotation.Blocking
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.UriInfo
import jakarta.transaction.TransactionSynchronizationRegistry
import jakarta.transaction.RollbackException
import java.util.concurrent.atomic.AtomicBoolean

/** HTTP controls for this unpublished test application, never library endpoints. */
@Path("/harness")
@Blocking
@Produces(MediaType.APPLICATION_JSON)
class HarnessResource {
  @field:Inject lateinit var engine: EngineFixture
  @field:Inject lateinit var registry: IdempotencyRegistry
  @field:Inject lateinit var meters: MeterRegistry
  @field:Inject lateinit var deployment: ProcessDeployment
  @field:Inject lateinit var config: WorkerConfig
  @field:Inject lateinit var synchronizations: TransactionSynchronizationRegistry

  @GET
  @Path("/runtime")
  fun runtime(@Context uri: UriInfo): Map<String, Any?> =
    mapOf("port" to uri.baseUri.port, "pid" to ProcessHandle.current().pid(),
      "imageCode" to System.getProperty("org.graalvm.nativeimage.imagecode", "jvm"))

  @GET
  fun overview(): Map<String, Any?> = LinkedHashMap(engine.overview()).apply {
    put("removeOnCompletion", config.removeTaskResultOnCompletion())
  }

  @POST
  @Path("/deliver/{topic}/{id}")
  @Consumes(MediaType.APPLICATION_JSON)
  fun deliver(@PathParam("topic") topic: String, @PathParam("id") id: String,
    payload: Map<String, Any?>, @QueryParam("ambient") ambient: Boolean,
    @QueryParam("rollbackAmbient") rollbackAmbient: Boolean): Map<String, Any?> {
    val delivery = Runnable {
      engine.endpointTransaction(id, synchronizations.transactionKey)
      engine.deliver(topic, task(id, "worker-process"), payload)
    }
    if (ambient) deliverInAmbientTransaction(delivery, rollbackAmbient) else delivery.run()
    return engine.taskState(id)
  }

  private fun deliverInAmbientTransaction(delivery: Runnable, rollbackAmbient: Boolean) {
    val rollbackRequested = AtomicBoolean()
    try {
      QuarkusTransaction.requiringNew().run {
        delivery.run()
        if (rollbackAmbient) {
          QuarkusTransaction.setRollbackOnly()
          rollbackRequested.set(true)
        }
      }
    } catch (exception: QuarkusTransactionException) {
      // The runner attempts commit on return, including an explicitly rollback-only transaction.
      if (!rollbackRequested.get() || exception.cause !is RollbackException) throw exception
    }
  }

  @GET
  @Path("/tasks/{id}")
  fun taskState(@PathParam("id") id: String): Map<String, Any?> = engine.taskState(id)

  @POST
  @Path("/completion/{id}/{mode}")
  fun configureCompletion(@PathParam("id") id: String, @PathParam("mode") mode: String) {
    engine.configureCompletion(id, mode)
  }

  @POST
  @Path("/release/{id}")
  fun release(@PathParam("id") id: String, @QueryParam("success") success: Boolean) {
    engine.release(id, success)
  }

  @POST
  @Path("/registry/{id}")
  @Consumes(MediaType.APPLICATION_JSON)
  fun register(@PathParam("id") id: String, @QueryParam("process") process: String, result: Map<String, Any?>) {
    registry.register(task(id, process), result)
  }

  @POST
  @Path("/registry/{id}/samples")
  fun registerSamples(@PathParam("id") id: String) {
    registry.register(task(id, "serialization"), SerializationSamples.create())
  }

  @GET
  @Path("/registry/{id}")
  fun read(@PathParam("id") id: String): Map<String, Any?> {
    val result = registry.getTaskResult(task(id, "unused"))
    return linkedMapOf("exists" to (result != null), "empty" to (result != null && result.isEmpty()), "result" to result)
  }

  @GET
  @Path("/registry/{id}/samples")
  fun verifySamples(@PathParam("id") id: String): Map<String, Any?> {
    // Each call uses the registry's fresh transaction, never a request-scoped cached entity.
    val result = registry.getTaskResult(task(id, "unused"))
    return mapOf("matches" to SerializationSamples.matches(result),
      "dtoClass" to (if (result == null) "missing" else result.getValue("dto")!!.javaClass.name),
      "containerClass" to (if (result == null) "missing" else result.getValue("immutable")!!.javaClass.name))
  }

  @DELETE
  @Path("/registry/{id}")
  fun remove(@PathParam("id") id: String) { registry.removeTaskResult(id) }

  @DELETE
  @Path("/process/{id}")
  fun removeProcess(@PathParam("id") id: String) { registry.removeAllTaskResults(id) }

  @POST
  @Path("/deploy")
  fun deploy(): Map<String, String> {
    val result = checkNotNull(deployment.deployResources()) {
      "Acceptance fixture requires a deployment client and packaged resources"
    }
    return mapOf("key" to result.deploymentKey)
  }

  @GET
  @Path("/metrics/{topic}")
  fun metrics(@PathParam("topic") topic: String): Map<String, Double> {
    val result = linkedMapOf<String, Double>()
    for (suffix in listOf("received", "completed", "bpmn.error", "failed")) {
      val counter = meters.find("bpmcrafters.worker.external.tasks.$suffix").tag("topic", topic).counter()
      result[suffix] = counter?.count() ?: 0.0
    }
    return result
  }

  private fun task(id: String, process: String): TaskInformation =
    TaskInformation(id, mapOf(CommonRestrictions.PROCESS_INSTANCE_ID to process))
}
