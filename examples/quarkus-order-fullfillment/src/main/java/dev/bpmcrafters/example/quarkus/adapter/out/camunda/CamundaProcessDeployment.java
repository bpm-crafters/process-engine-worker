package dev.bpmcrafters.example.quarkus.adapter.out.camunda;

import dev.bpmcrafters.example.quarkus.application.port.out.ProcessDeployment;
import dev.bpmcrafters.example.quarkus.domain.DeploymentResult;
import dev.bpmcrafters.processengineapi.deploy.DeployBundleCommand;
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi;
import dev.bpmcrafters.processengineapi.deploy.NamedResource;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class CamundaProcessDeployment implements ProcessDeployment {
  private final DeploymentApi deployment;

  public CamundaProcessDeployment(DeploymentApi deployment) { this.deployment = deployment; }

  @Override
  public DeploymentResult deploy() throws Exception {
    final NamedResource resource = NamedResource.fromClasspath("C8/Order Fulfillment.bpmn");
    try (final var stream = resource.getResourceStream()) {
      final var result = deployment.deploy(new DeployBundleCommand(List.of(resource), null)).get();
      return new DeploymentResult(result.getDeploymentKey(), result.getDeploymentTime(), result.getTenantId());
    }
  }
}
