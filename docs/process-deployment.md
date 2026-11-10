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

Startup deployment is disabled by default. Supply a CDI `DeploymentApi` bean and set `dev.bpm-crafters.process-api.worker.deployment.enabled=true`.

Add the Process Engine Worker dependency to your projects classpath and you are ready to go. In Maven add the following to your `pom.xml`:

```xml
<dependency>
  <groupId>dev.bpm-crafters.process-engine-worker</groupId>
  <artifactId>process-engine-worker-quarkus</artifactId>
  <version>${process-engine-worker.version}</version>
</dependency>
```

Provide a `DeploymentApi` bean manually or use the process-engine-api quarkus extension adapter for your engine.
The bpmn and dmn files should be placed for deployment on the classpath like e.g. the `src/main/resources/**` folders

You can configure the deployment by adding the following properties to your `application.properties` or `application.yml`:

```yaml
dev:
  bpm-crafters:
    process-api:
      worker:
        deployment:
          enabled: true # Enable or disable the deployment
          deployment-timeout-in-seconds: 30
          bpmn-resource-pattern: "classpath*:/**/*.bpmn"
          dmn-resource-pattern: "classpath*:/**/*.dmn"
```

For manual deployment, inject
`dev.bpmcrafters.processengine.worker.quarkus.deploymentresource.ProcessDeployment`
and call `deployResources()`.

**Note:** Deployment resources are fixed in quarkus at build time. You have to rebuild the application to deploy changes in bpmn, dmn, ... .
