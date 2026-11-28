package dev.bpmcrafters.processengine.worker.configuration

import com.fasterxml.jackson.databind.ObjectMapper as Jackson2ObjectMapper
import dev.bpmcrafters.processengine.worker.idempotency.IdempotencyRegistry
import dev.bpmcrafters.processengine.worker.idempotency.NoOpIdempotencyRegistry
import dev.bpmcrafters.processengine.worker.registrar.*
import dev.bpmcrafters.processengine.worker.registrar.metrics.ProcessEngineWorkerMetricsMicrometer
import dev.bpmcrafters.processengine.worker.registrar.metrics.ProcessEngineWorkerMetricsNoOp
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper as Jackson3ObjectMapper

/**
 * Auto configuration.
 * @since 0.5.0
 **/
@AutoConfiguration(afterName = [
  "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration",
  "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration",
  "org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration",
  "org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration",
  "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration",
])
@EnableConfigurationProperties(ProcessEngineWorkerProperties::class)
class ProcessEngineWorkerAutoConfiguration {

  /**
   * Configures the worker converter from the application's Jackson 3 mapper.
   * @since 0.9.0
   **/
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(Jackson3ObjectMapper::class)
  @ConditionalOnBean(Jackson3ObjectMapper::class)
  class Jackson3ConverterConfiguration {
    /**
     * Creates the default converter using the Jackson 3 mapper bean.
     **/
    @Bean
    @ConditionalOnMissingBean(VariableConverter::class)
    fun defaultJacksonVariableConverter(objectMapper: Jackson3ObjectMapper): VariableConverter =
      Jackson3VariableConverter(objectMapper)
  }

  /**
   * Configures the worker converter from Jackson 2 when a Jackson 3 mapper bean is unavailable.
   * @since 0.9.0
   **/
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(Jackson2ObjectMapper::class)
  @ConditionalOnBean(Jackson2ObjectMapper::class)
  class Jackson2ConverterConfiguration {
    /**
     * Creates the fallback converter using the Jackson 2 mapper bean.
     **/
    @Bean
    @ConditionalOnMissingBean(value = [VariableConverter::class], type = ["tools.jackson.databind.ObjectMapper"])
    fun defaultJacksonVariableConverter(objectMapper: Jackson2ObjectMapper): VariableConverter =
      Jackson2VariableConverter(objectMapper)
  }

  /**
   * Initializes parameter resolver.
   * If you want to add your own parameter resolver, create your own parameter resolver and expose it as bean. You might want
   * to register your own strategies using the [ParameterResolver.ParameterResolverBuilder#addStrategy] method.
   **/
  @Bean
  @ConditionalOnMissingBean
  fun defaultParameterResolver() = ParameterResolver.builder().build()

  /**
   * Initializes result resolver.
   * If you want to add your own result resolver, create your own result resolver and expose it as a bean.
   * You might want to register your own strategy using the [ResultResolver.ResultResolverBuilder#addStrategy] method.
   **/
  @Bean
  @ConditionalOnMissingBean
  fun defaultResultResolver() = ResultResolver.builder().build()

  /**
   * Micrometer-based metrics.
   **/
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(MeterRegistry::class)
  @ConditionalOnBean(MeterRegistry::class)
  class MicrometerMetricsConfiguration {
    @Bean
    @ConditionalOnMissingBean(ProcessEngineWorkerMetrics::class)
    fun processEngineWorkerMetricsMicrometer(meterRegistry: MeterRegistry): ProcessEngineWorkerMetrics =
      ProcessEngineWorkerMetricsMicrometer(meterRegistry = meterRegistry)
  }

  /**
   * Empty metrics implementation skipping metrics delivery.
   **/
  @Bean
  @ConditionalOnMissingBean(ProcessEngineWorkerMetrics::class)
  fun processEngineWorkerMetricsNoOp(): ProcessEngineWorkerMetrics {
    return ProcessEngineWorkerMetricsNoOp
  }

  /**
   * Fallback to a no-op idempotency registry.
   **/
  @Bean
  @ConditionalOnMissingBean(IdempotencyRegistry::class)
  fun defaultIdempotencyRegistry(): IdempotencyRegistry = NoOpIdempotencyRegistry()

}
