package dev.bpmcrafters.processengine.worker.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import dev.bpmcrafters.processengine.worker.registrar.JacksonVariableConverter
import dev.bpmcrafters.processengine.worker.registrar.ParameterResolver
import dev.bpmcrafters.processengine.worker.registrar.ResultResolver
import dev.bpmcrafters.processengine.worker.registrar.VariableConverter
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Auto configuration.
 */
@Configuration
@EnableConfigurationProperties(ProcessEngineWorkerProperties::class)
class ProcessEngineWorkerAutoConfiguration {

  /**
   * Initializes the converter.
   */
  @Bean
  @ConditionalOnMissingBean
  fun defaultJacksonVariableConverter(objectMapper: ObjectMapper): VariableConverter
    = JacksonVariableConverter(objectMapper = objectMapper)

  /**
   * Initializes parameter resolver.
   * If you want to add your own parameter resolver, just create your own parameter resolver and expose it as bean. You might want
   * to register your own strategies using the [ParameterResolver.ParameterResolverBuilder#addStrategy] method.
   */
  @Bean
  @ConditionalOnMissingBean
  fun defaultParameterResolver() = ParameterResolver.builder().build()

  /**
   * Initializes result resolver.
   * If you want to add your own result resolver, just create you own result resolver and expose it as a bean. You might want
   * to register you own strategy using [ResultResolver.ResultResolverBuilder#addStrategy] method.
   */
  @Bean
  @ConditionalOnMissingBean
  fun defaultResultResolver() = ResultResolver.builder().build()
}
