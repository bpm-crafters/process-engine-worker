package dev.bpmcrafters.processengine.worker.converter

import com.fasterxml.jackson.databind.ObjectMapper
import dev.bpmcrafters.processengine.worker.registrar.ParameterResolver
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Variable converter configuration.
 */
@Configuration
class VariableConverterConfiguration {

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
