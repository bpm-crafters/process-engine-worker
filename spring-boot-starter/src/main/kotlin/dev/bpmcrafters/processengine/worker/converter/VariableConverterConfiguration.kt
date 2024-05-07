package dev.bpmcrafters.processengine.worker.converter

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class VariableConverterConfiguration {

  @Bean
  fun variableConverter(objectMapper: ObjectMapper): VariableConverter = VariableConverter(objectMapper = objectMapper)
}
