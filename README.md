# Process Engine API Worker


[![incubating](https://img.shields.io/badge/lifecycle-INCUBATING-orange.svg)](https://github.com/holisticon#open-source-lifecycle)
[![Development branches](https://github.com/bpm-crafters/process-engine-worker/actions/workflows/development.yml/badge.svg)](https://github.com/bpm-crafters/process-engine-worker/actions/workflows/development.yml)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/dev.bpm-crafters.process-engine-api/process-engine-worker/badge.svg)](https://maven-badges.herokuapp.com/maven-central/dev.bpm-crafters.process-engine-api/process-engine-worker)

## Purpose of the library

A small opinionated annotated-based SpringBoot worker implementation for creation of external task workers using Process-Engine-API.

## How to use 

Add the following dependency to your project's class path:

```xml
<dependency>
  <groupId>dev.bpm-crafters.process-engine-worker</groupId>
  <artifactId>process-engine-worker-spring-boot-starter</artifactId>
</dependency>
```

Provide a Spring Component and annotate its method as following:

```java 

@Component
public class MyWorker {
  
  @ProcessEngineWorker(topic = "fetchGoods")
  public void fetchGoods(
    TaskInformation taskInformation,
    ExternalTaskCompletionApi externalTaskCompletionApi,
    @Variable(name = "order") Order order
  ) {
    // execute some business code

    // complete the task using process engine API
    externalTaskCompletionApi.completeTask(
      new CompleteTaskCmd(taskInformation.getTaskId(), () -> Map.of("shipped", true))
    ).get();
  }
}
```

## Examples

There is an `Order fulfillment` example, you can easily try out. It follows the approach of clean architecture
and uses Process Engine API and Process Engine Worker libraries. To run it, you have two options:

### Running locally

1. Start `docker-compose.yaml` (this will start containerized Zeebe locally)
2. Start `FulfillmentProcessApplication` activating Spring profile `local`.

### Running using Camunda SaaS

- Start `FulfillmentProcessApplication` activating Spring profile `cloud` and pass the following environment variables:

```properties
ZEEBE_REGION=..
ZEEBE_CLUSTER_ID=..
ZEEBE_CLIENT_ID=..
ZEEBE_CLIENT_SECRET=...
```



