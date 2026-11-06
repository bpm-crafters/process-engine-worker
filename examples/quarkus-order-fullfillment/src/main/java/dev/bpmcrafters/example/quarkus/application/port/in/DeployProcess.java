package dev.bpmcrafters.example.quarkus.application.port.in;

import dev.bpmcrafters.example.quarkus.domain.DeploymentResult;

public interface DeployProcess {
  DeploymentResult deploy() throws Exception;
}
