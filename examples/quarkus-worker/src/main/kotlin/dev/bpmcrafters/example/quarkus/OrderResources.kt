package dev.bpmcrafters.example.quarkus

import dev.bpmcrafters.processengineapi.deploy.DeployBundleCommand
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.deploy.DeploymentInformation
import dev.bpmcrafters.processengineapi.deploy.NamedResource
import dev.bpmcrafters.processengineapi.process.StartProcessApi
import dev.bpmcrafters.processengineapi.process.StartProcessByMessageCmd
import io.smallrye.common.annotation.Blocking
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/ingress")
@Consumes(MediaType.APPLICATION_JSON)
@Blocking
class IngressResource(private val start: StartProcessApi) {
  @POST
  @Path("/order-received")
  fun receive(request: OrderRequest?): Response {
    val order = request?.toDomain() ?: throw BadRequestException("Order required")
    start.startProcess(StartProcessByMessageCmd("Msg_OrderReceived", { mapOf("order" to order) }, emptyMap())).get()
    return Response.noContent().build()
  }
}

data class DeploymentRequest(val path: String? = null)

@Path("/deploy")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Blocking
class DeploymentResource(private val deployment: DeploymentApi) {
  @POST
  fun deploy(request: DeploymentRequest?): DeploymentInformation {
    if (request?.path != PROCESS_RESOURCE) throw BadRequestException("Only $PROCESS_RESOURCE can be deployed")
    val resource = NamedResource.fromClasspath(PROCESS_RESOURCE)
    return resource.resourceStream.use { deployment.deploy(DeployBundleCommand(listOf(resource), null)).get() }
  }
}

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
class NotificationResource(private val pool: NotificationPool) {
  @GET
  @Path("user-tasks")
  fun tasks() = pool.list()

  @GET
  @Path("failed-payment/{taskId}")
  fun failed(@PathParam("taskId") id: String) = pool.get(id, FAILED_PAYMENT)

  @GET
  @Path("delayed-inventory/{taskId}")
  fun delayed(@PathParam("taskId") id: String) = pool.get(id, DELAYED_INVENTORY)

  @POST
  @Path("failed-payment/{taskId}")
  fun completeFailed(@PathParam("taskId") id: String): Response {
    pool.complete(id, FAILED_PAYMENT)
    return Response.noContent().build()
  }

  @POST
  @Path("delayed-inventory/{taskId}")
  fun completeDelayed(@PathParam("taskId") id: String): Response {
    pool.complete(id, DELAYED_INVENTORY)
    return Response.noContent().build()
  }
}
