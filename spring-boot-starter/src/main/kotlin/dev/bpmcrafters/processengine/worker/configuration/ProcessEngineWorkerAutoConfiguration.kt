package dev.bpmcrafters.processengine.worker.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import dev.bpmcrafters.processengine.worker.registrar.VariableConverter
import dev.bpmcrafters.processengine.worker.registrar.ParameterResolver
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Auto configuration.
 */
@Configuration
class ProcessEngineWorkerAutoConfiguration {

  /**
   * Creates a default fixed thread pool for 10 threads used for process engine worker executions.
   */
  @Bean
  @ConditionalOnMissingBean
  @Qualifier("processEngineWorkerTaskExecutor")
  fun processEngineWorkerTaskExecutor(): ExecutorService = Executors.newFixedThreadPool(10)

  /**
   * Initializes the converter.
   */
  @Bean
  @ConditionalOnMissingBean
  fun defaultVariableConverter(objectMapper: ObjectMapper): VariableConverter = VariableConverter(objectMapper = objectMapper)

  /**
   * Initializes parameter resolver.
   * If you want to add your own parameter resolver, just create your own parameter resolver and expose it as bean. You might want
   * to register your own strategies using the [ParameterResolver.ParameterResolverBuilder#addStrategy] method.
   */
  @Bean
  @ConditionalOnMissingBean
  fun defaultParameterResolver() = ParameterResolver.builder().build()
}
