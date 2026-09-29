package dev.bpmcrafters.processengine.worker.quarkus.deployment.workers;

import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry;
import dev.bpmcrafters.processengine.worker.idempotency.InMemoryIdempotencyRegistry;
import dev.bpmcrafters.processengine.worker.registrar.ParameterResolutionStrategy;
import dev.bpmcrafters.processengine.worker.registrar.ParameterResolver;
import dev.bpmcrafters.processengine.worker.registrar.ResultResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.lang.reflect.Parameter;
import java.util.Map;

/**
 * Application-provided beans replacing the defaults of the extension.
 */
@ApplicationScoped
public class CustomBeans {

  /**
   * Custom parameter type resolved by the custom strategy.
   */
  public record Greeting(String text) {
  }

  @Produces
  @Singleton
  public ParameterResolver parameterResolver() {
    return ParameterResolver.builder().addStrategy(new ParameterResolutionStrategy() {
      @Override
      public boolean test(Parameter parameter) {
        return Greeting.class.equals(parameter.getType());
      }

      @Override
      public Object apply(Wrapper wrapper) {
        return new Greeting("hello " + wrapper.getTaskInformation().getTaskId());
      }
    }).build();
  }

  @Produces
  @Singleton
  public ResultResolver resultResolver() {
    return ResultResolver.builder().addStrategy(new ResultResolver.ResultResolutionStrategy(
      method -> String.class.equals(method.getReturnType()),
      result -> Map.of("text", result)
    )).build();
  }

  @Produces
  @Singleton
  public IdempotencyRegistry idempotencyRegistry() {
    return new InMemoryIdempotencyRegistry();
  }
}
