package dev.bpmcrafters.example.quarkus.application.usecase;

import dev.bpmcrafters.example.quarkus.application.port.in.DeployProcess;
import dev.bpmcrafters.example.quarkus.application.port.out.ProcessDeployment;
import dev.bpmcrafters.example.quarkus.domain.DeploymentResult;

public final class DeployProcessUseCase implements DeployProcess {
  private final ProcessDeployment deployment;

  public DeployProcessUseCase(ProcessDeployment deployment) { this.deployment = deployment; }

  @Override
  public DeploymentResult deploy() throws Exception { return deployment.deploy(); }
}
