---
title: Process Engine Worker
---

Process Engine Worker is an independent component built on top of Process Engine API in order to accelerate the development of agnostic workers
for any process engine supported by Process Engine API in Spring Boot ecosystem. By doing so, it abstracts from specific worker clients and 
API and allows to build universal workers.

First of all add the Process Engine Worker dependency to your projects classpath. In Maven add the following to you `pom.xml`:

```xml
<dependency>
  <groupId>dev.bpm-crafters.process-engine-worker</groupId>
  <artifactId>process-engine-worker-spring-boot-starter</artifactId>
  <version>${process-engine-worker.version}</version>
</dependency>
```

Now create a simple Spring component and annotate a method with a special annotation `@ProcessEngineWorker`:

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

## Method parameter resolution

Parameter resolution of the method annotated with `ProcessEngineWorker` is based on a set of strategies
registered by the `ParameterResolver` bean. Currently, the following parameters are resolved:

| Type                                   | Purpose                                                                   |
|----------------------------------------|---------------------------------------------------------------------------|
| TaskInformation                        | Helper abstracting all information about the external task.               |
| ExternTaskCompletionApi                | API for completing the external task manually                             |
| VariableConverter                      | Special utility to read the process variable map and deliver typed value  | 
| Map<String, Object>                    | Payload object containing all variables.                                  |
| Type annotated with @Variable("name")  | Marker for a process variable.                                            |

Usually, the requested variable is mandatory and the parameter resolver reports an error, if the requested variable is not
available in the process payload. If you want to inject the variable only if it exists in the payload you have two options.
Either you set the parameter `@Variable(name = "...", mandatory = false)` or you use `Optional<T>` instead of `T` as a variable
type. If you are using Kotlin and don't like `Optional`, make sure to declare variable type as nullable (`T?` instead of `T`) and
set the mandatory flag to `false`. 

## Method return type

If the return type of the method is of type `Map<String, Object>` or compatible and the `autoComplete` flag is turned
on the annotation (defaults to `true`), the library will try to automatically complete the External Task
using the returned map as completion variables. If `autoComplete` is `true`, but no return value is provided, the task
will be completed without empty payload. This functionality is provided by the `ResultResolver` based on registered strategies.

## Throwing a BPMN Error

If you want to throw a BPMN error, please throw an instance of a `BPMNErrorOccured` exception from the method body. The exception
is a checked exception, in order to comply with Spring behavior of not rolling back transaction on checked exceptions.

## Transactional support

The worker method can be marked transactional by adding Spring or Jakarta EE transactional annotations to the method or to the worker class.
If the annotation `@org.springframework.transaction.annotation.Transactional` with propagation `REQUIRES`, `REQUIRES_NEW`, `SUPPORTS` and `MANDATORY`
is used, the library will execute the worker method and the completion of the external task via API in the same transaction. This will lead to 
a transaction rollback, if the external task can't be completed (e.g. due to a network error).






