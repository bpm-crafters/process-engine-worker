package dev.bpmcrafters.example.quarkus.application.port.out;

import dev.bpmcrafters.example.quarkus.domain.DeploymentResult;

public interface ProcessDeployment {
  DeploymentResult deploy() throws Exception;
}
