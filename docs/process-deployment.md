---
title: Process Deployment
---

Process Deployment supports deployments of process resources like BPMN and DMN files to the Process Engine.
It is built on top of the Process Engine API and provides a simple way to deploy process resources automatically on spring boot startup.

Add the Process Engine Worker dependency to your projects classpath and you are ready to go. In Maven add the following to your `pom.xml`:

```xml
<dependency>
  <groupId>dev.bpm-crafters.process-engine-worker</groupId>
  <artifactId>process-engine-worker-spring-boot-starter</artifactId>
  <version>${process-engine-worker.version}</version>
</dependency>
```

Enable auto-deployment to deploy matching BPMN and DMN resources to the process engine during startup.
The starter uses the adapter's `DeploymentApi` at the `SUBSCRIPTIONS_REGISTERED` lifecycle phase,
after the initial worker subscriptions are in place and before the adapter's initial pull and normal task delivery.
The lifecycle waits for deployment to complete before continuing.

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

Auto-deployment requires an adapter that drives the lifecycle and provides a `DeploymentApi` bean.
