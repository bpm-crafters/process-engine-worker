# Process Engine API Worker


[![incubating](https://img.shields.io/badge/lifecycle-INCUBATING-orange.svg)](https://github.com/holisticon#open-source-lifecycle)
[![Development branches](https://github.com/bpm-crafters/process-engine-worker/actions/workflows/development.yml/badge.svg)](https://github.com/bpm-crafters/process-engine-worker/actions/workflows/development.yml)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/dev.bpm-crafters.process-engine-api/process-engine-worker/badge.svg)](https://maven-badges.herokuapp.com/maven-central/dev.bpm-crafters.process-engine-api/process-engine-worker)

## Purpose of the library

A small opinionated annotated-based SpringBoot worker implementation for creation of 
external task workers using Process-Engine-API.

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

Parameter resolution of the method annotated with `ProcessEngineWorker` is based on a set of strategies
registered by the `ParameterResolver` bean. Currently, the following parameters are resolved:

| Type                                   | Purpose                                                     |
|----------------------------------------|-------------------------------------------------------------|
| TaskInformation                        | Helper abstracting all information about the external task. |
| ExternTaskCompletionApi                | API for completing the external task manually               |
| Map<String, Object>                    | Payload object containing all variables.                    |
| Type annotated with @Variable("name)   | Marker for a process variable.                              |

If the return type of the method is of type `Map<String, Object>` or compatible, the registrar will try to 
automatically complete the External Task using the returned map as completion variables.

You might want to register your own parameter resolution strategies. For this purpose, please construct 
the parameter resolver bean on your own and register your own strategies:

```kotlin
import java.beans.BeanProperty

@Configuration
class MyConfig {

  @Bean
  fun myParameterResolver(): ParameterResolver {
    return ParameterResolver.builder().addStrategy(
      ParameterResolutionStrategy(
        parameterMatcher = { param -> ... },
        parameterExtractor = { param, taskInformation, payload, variableConverter, taskCompletionApi -> ... },
      ),
    ).build()
  }

}

```

## Examples

There is an `Order fulfillment` example, you can easily try out. It follows the approach of clean architecture
and uses Process Engine API and Process Engine Worker libraries. To run it, you have two options:

### Running locally using Camunda 7 embedded

1. Start `FulfillmentProcessApplication` activating profile `c7embedded`.

### Running locally using self-managed Camunda 8

1. Start `docker-compose.yaml` (this will start containerized Zeebe locally)
2. Start `FulfillmentProcessApplication` activating Spring profile `local`.

### Running using Camunda SaaS

1. Start `FulfillmentProcessApplication` activating Spring profile `cloud` and pass the following environment variables:

```properties
ZEEBE_REGION=..
ZEEBE_CLUSTER_ID=..
ZEEBE_CLIENT_ID=..
ZEEBE_CLIENT_SECRET=...
```

