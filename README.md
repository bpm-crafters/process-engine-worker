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

Provide a Spring Component and annotate its method as following and doing everything else manually:

```java 

@Component
@RequiredArgsConstructor
public class MyWorker {
  
  private final FetchGoodsInPort fetchGoodsInPort;
  
  @ProcessEngineWorker(topic = "fetchGoods", autoComplete = false)
  public void fetchGoods(
    TaskInformation taskInformation,
    ExternalTaskCompletionApi externalTaskCompletionApi,
    VariableConverter variableConverter,
    Map<String, Object> processPayload
  ) {
    var order = variableConverter.mapToType(payload.get("order"), Order.class);

    // execute some business code
    var fetched = fetchGoodsInPort.fetchGoods(order);

    // complete the task using process engine API
    externalTaskCompletionApi.completeTask(
      new CompleteTaskCmd(taskInformation.getTaskId(), () -> Map.of("shipped", fetched))
    ).get();
  }
}
```

You can get it even more convenient, if you want the worker to auto complete the task after
the execution and use the automatic variable conversion:

```java
@Component
public class MySmartWorker {

  @ProcessEngineWorker(topic = "fetchGoods")
  public void fetchGoods(
    @Variable(name = "order") Order order
  ) {
    // execute some business code
    var fetched = fetchGoodsInPort.fetchGoods(order);
    
    return Map.of("shipped", fetched);
  }
}

```

Parameter resolution of the method annotated with `ProcessEngineWorker` is based on a set of strategies
registered by the `ParameterResolver` bean. Currently, the following parameters are resolved:

| Type                                   | Purpose                                                                   |
|----------------------------------------|---------------------------------------------------------------------------|
| TaskInformation                        | Helper abstracting all information about the external task.               |
| ExternTaskCompletionApi                | API for completing the external task manually                             |
| VariableConverter                      | Special utility to read the process variable map and deliver typed value  | 
| Map<String, Object>                    | Payload object containing all variables.                                  |
| Type annotated with @Variable("name)   | Marker for a process variable.                                            |

If the return type of the method is of type `Map<String, Object>` or compatible and the `autoComplete` flag is turned
on the annotation is `true` (defaults to `true` ), the registrar will try to automatically complete the External Task 
using the returned map as completion variables. If `autoComplete` is true, but not return value is provided, the task
will be completed without payload. 

If you want to throw a BPMN error, please throw an instance of a `BPMNErrorOccured`.

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

There is an `Order fulfillment` example, you can easily try out. It follows the approach of 
clean architecture and uses Process Engine API and Process Engine Worker libraries. 
To run it, you have several options:

### Running locally using Camunda 7 embedded

1. Start `FulfillmentProcessApplication` activating profile `c7embedded` Spring profile.

### Running locally using self-managed Camunda 8

1. Start `docker-compose.yaml` (this will start containerized Zeebe locally)
2. Start `FulfillmentProcessApplication` activating Spring profile `c8sm`.

### Running using Camunda SaaS

1. Start `FulfillmentProcessApplication` activating Spring profile `c8cloud` and pass the following environment variables:

```properties
ZEEBE_REGION=..
ZEEBE_CLUSTER_ID=..
ZEEBE_CLIENT_ID=..
ZEEBE_CLIENT_SECRET=...
```

After starting application, you can either use Open API endpoints or just run the 
HTTP client tests using your IntelliJ, located in the example directory.
