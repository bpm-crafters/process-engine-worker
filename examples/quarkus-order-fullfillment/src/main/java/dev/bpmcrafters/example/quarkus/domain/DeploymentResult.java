package dev.bpmcrafters.example.quarkus.domain;

import java.time.Instant;

public record DeploymentResult(String deploymentKey, Instant deploymentTime, String tenantId) { }
