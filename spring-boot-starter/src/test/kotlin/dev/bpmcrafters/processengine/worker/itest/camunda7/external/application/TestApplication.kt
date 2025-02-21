package dev.bpmcrafters.processengine.worker.itest.camunda7.external.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.camunda.community.rest.EnableCamundaRestClient
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import java.text.SimpleDateFormat

@SpringBootApplication
@EnableCamundaRestClient
class TestApplication {

  @Bean
  fun objectMapper(): ObjectMapper = jacksonObjectMapper().apply{
    registerModule(Jdk8Module())
    registerModule(JavaTimeModule())
    dateFormat = SimpleDateFormat("yyyy-MM-dd'T'hh:MM:ss.SSSz")
  }
}
