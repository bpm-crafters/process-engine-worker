package dev.bpmcrafters.example.quarkus.adapter.in.rest;

import dev.bpmcrafters.example.quarkus.application.port.in.DeployProcess;
import dev.bpmcrafters.example.quarkus.domain.DeploymentResult;
import io.smallrye.common.annotation.Blocking;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/deploy")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class DeploymentResource {
  public static final String PROCESS_RESOURCE = "C8/Order Fulfillment.bpmn";
  private final DeployProcess deployment;

  public DeploymentResource(DeployProcess deployment) { this.deployment = deployment; }

  public record DeploymentRequest(String path) { }

  @POST
  public DeploymentResult deploy(DeploymentRequest request) throws Exception {
    if (request == null || !PROCESS_RESOURCE.equals(request.path())) {
      throw new BadRequestException("Only " + PROCESS_RESOURCE + " can be deployed");
    }
    return deployment.deploy();
  }
}
