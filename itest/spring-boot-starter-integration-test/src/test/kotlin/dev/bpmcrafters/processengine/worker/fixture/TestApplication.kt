package dev.bpmcrafters.processengine.worker.fixture

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import java.text.SimpleDateFormat

@SpringBootApplication
@EntityScan(basePackageClasses = [MyEntity::class])
@EnableJpaRepositories(basePackageClasses = [MyEntityRepository::class])
class TestApplication {

  @Bean
  fun objectMapper(): ObjectMapper = jacksonObjectMapper().apply {
    registerModule(Jdk8Module())
    registerModule(JavaTimeModule())
    dateFormat = SimpleDateFormat("yyyy-MM-dd'T'hh:MM:ss.SSSz")
  }
}
