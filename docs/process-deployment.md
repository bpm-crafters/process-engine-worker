---
title: Process Deployment
---

Process Deployment supports deployments of process resources like BPMN and DMN files to the Process Engine.
It is built on top of Process Engine API and supports startup deployment in Spring Boot and Quarkus.

## Spring Boot

Add the Process Engine Worker dependency to your projects classpath and you are ready to go. In Maven add the following to your `pom.xml`:

```xml
<dependency>
  <groupId>dev.bpm-crafters.process-engine-worker</groupId>
  <artifactId>process-engine-worker-spring-boot-starter</artifactId>
  <version>${process-engine-worker.version}</version>
</dependency>
```

This will automatically deploy all bpmn and dmn files from the `src/main/resources/**` folders to the Process Engine on startup.

You can configure the deployment by adding the following properties to your `application.properties` or `application.yml`:

```yaml
dev:
  bpm-crafters:
    process-api:
      worker:
        deployment:
          enabled: true # Enable or disable the deployment
          bpmnResourcePattern: "classpath*:/**/*.bpmn"
          dmnResourcePattern: "classpath*:/**/*.dmn"
```

> **Warning:** Disabling the engine's own autodeployment also disables the autodeployment provided by the Process Engine Worker. In that setup, the Process Engine Worker deployment feature will not deploy resources on startup either. To use the Process Engine Worker's autodeployment feature, make sure to not explicitly disable the engine's process deployment features.

## Quarkus

The `process-engine-worker-quarkus` extension packages matching application and dependency-JAR
resources at build time, including them in native images. Supply an optional CDI `DeploymentApi`
bean from your engine adapter and enable startup deployment:

```properties
dev.bpm-crafters.process-api.worker.deployment.enabled=true
dev.bpm-crafters.process-api.worker.deployment.deployment-timeout-in-seconds=30
dev.bpm-crafters.process-api.worker.deployment.bpmn-resource-pattern=classpath*:/**/*.bpmn
dev.bpm-crafters.process-api.worker.deployment.dmn-resource-pattern=classpath*:/**/*.dmn
```

Deployment defaults to disabled. Patterns are build-time-fixed classpath globs;
only `classpath:` and `classpath*:` prefixes are supported, not filesystem or remote URLs.
Rebuild the application after changing patterns. Enablement and timeout are runtime settings.
Missing `DeploymentApi` skips startup deployment, as in Spring Boot. Startup deployment
is independent of the worker `enabled` and `register-process-workers` flags.

For manual deployment, inject
`dev.bpmcrafters.processengine.worker.quarkus.deploymentresource.ProcessDeployment`
and call `deployResources()`. It uses the same packaged resources and timeout.
See the [Quarkus guide](quarkus.md).
