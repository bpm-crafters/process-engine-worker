# Process Engine API Worker


[![incubating](https://img.shields.io/badge/lifecycle-INCUBATING-orange.svg)](https://github.com/holisticon#open-source-lifecycle)
[![Development branches](https://github.com/bpm-crafters/process-engine-worker/actions/workflows/development.yml/badge.svg)](https://github.com/bpm-crafters/process-engine-worker/actions/workflows/development.yml)
[![Maven Central Version](https://img.shields.io/maven-central/v/dev.bpm-crafters.process-engine-worker/process-engine-worker-spring-boot-starter)](https://central.sonatype.com/artifact/dev.bpm-crafters.process-engine-worker/process-engine-worker-spring-boot-starter)


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

The simplest way to implement the worker is to provide a component, with a worker method receiving typed
objects and returning a map of variables, which are automatically published as process variables.

```java
@Component
@RequiredArgsConstructor
public class MySmartWorker {

  private final FetchGoodsInPort fetchGoodsInPort;

  @ProcessEngineWorker(topic = "fetchGoods")
  public Map<String, Object> fetchGoods(
    @Variable(name = "order") Order order
  ) {
    // execute some business code
    var fetched = fetchGoodsInPort.fetchGoods(order);
    
    return Map.of("shipped", fetched);
  }
}

```

If you need more control in your worker, you can supply more parameters in your worker method, including the
`ExternalTaskCompletionApi` and invoke it manually:

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

Parameter resolution of the method annotated with `ProcessEngineWorker` is based on a set of strategies
registered by the `ParameterResolver` bean. Currently, the following parameters are resolved:

| Type                                   | Purpose                                                                   |
|----------------------------------------|---------------------------------------------------------------------------|
| TaskInformation                        | Helper abstracting all information about the external task.               |
| ExternTaskCompletionApi                | API for completing the external task manually                             |
| VariableConverter                      | Special utility to read the process variable map and deliver typed value  | 
| Map<String, Object>                    | Payload object containing all variables.                                  |
| Type annotated with @Variable("name)   | Marker for a process variable.                                            |

Usually, the requested variable is mandatory and the parameter resolver reports an error, if the requested variable is not 
available in the process payload. If you want to inject the variable only if it exists in the payload you have two options.
Either you set the parameter `@Variable(name = "...", mandatory = false)` or you use `Optional<T>` instead of `T` as a variable
type. If you are using Kotlin and don't like `Optional`, make sure to declare variable type as nullable (`T?` instead of `T`) and 
set the mandatory flag to `false`. 

If the return type of the method is of type `Map<String, Object>` or compatible and the `autoComplete` flag is turned
on the annotation is `true` (defaults to `true`), the library will try to automatically complete the External Task 
using the returned map as completion variables. If `autoComplete` is `true`, but no return value is provided, the task
will be completed without empty payload. This functionality is provided by the `ResultResolver` based on registered strategies.

If you want to throw a BPMN error, please throw an instance of a `BPMNErrorOccured`.

## Customizations

You might want to register your own parameter resolution strategies. For this purpose, please construct 
the parameter resolver bean on your own and register your own strategies:

```kotlin

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

Optionally, you might want to register own result resolution strategies. For this purpose, please construct
the result resolver bean on your own and register your own strategies:

```kotlin

@Configuration
class MyConfig {

  @Bean
  fun myResultResolver(): ResultResolver {
    return ResultResolver.builder().addStrategy(
      ResultResolutionStrategy(
        resultMatcher = { method -> ... },
        resultConverter = { result -> ... },
      ),
    ).build()
  }
}

```

If you want to switch the entire library off (for example you are in the context of an integrtion test, and parts of your Process Engine API are deactivated),
you can do it by setting the property `dev.bpm-crafters.process-api.worker.enabled` to `false`.

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

Don't forget to first deploy the process! Either manually via operate / modeler, or with the HTTP client script: c8-deploy-process.http
