package dev.bpmcrafters.processengine.worker

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import dev.bpmcrafters.processengine.worker.TestHelper.Camunda7RunTestContainer
import feign.Logger
import org.assertj.core.api.Assertions.assertThat
import org.camunda.bpm.engine.RepositoryService
import org.camunda.community.rest.EnableCamundaRestClient
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.text.SimpleDateFormat

@SpringBootTest
@Testcontainers
@ActiveProfiles("itest")
class TransactionalBehaviorITest {

  @Configuration
  @EnableCamundaRestClient
  @SpringBootApplication
  class Config {

    @Bean
    fun objectMapper(): ObjectMapper {
      val mapper = ObjectMapper()
      mapper.registerModule(Jdk8Module())
      mapper.registerModule(JavaTimeModule())
      mapper.registerModule(KotlinModule.Builder().build())
      mapper.setDateFormat(SimpleDateFormat("yyyy-MM-dd'T'hh:MM:ss.SSSz"))
      return mapper
    }


  }

  companion object {

    @Container
    @JvmStatic
    val camundaContainer = Camunda7RunTestContainer("run-7.22.0")

    @Container
    @JvmStatic
    val postgresContainer = TestHelper.postgresContainer()

    @JvmStatic
    @DynamicPropertySource
    fun configure(registry: DynamicPropertyRegistry) {
      registry.add("spring.datasource.url") { postgresContainer.jdbcUrl }
      registry.add("spring.datasource.username") { postgresContainer.username }
      registry.add("spring.datasource.password") { postgresContainer.password }
      registry.add("spring.datasource.driver-class-name") { postgresContainer.driverClassName }
      registry.add("feign.client.config.default.url") { "http://localhost:${camundaContainer.firstMappedPort}/engine-rest/" }
    }
  }


  @Autowired
  private lateinit var repositoryService: RepositoryService


  @Test
  fun `makes sure process gets deployed with remote rest client`() {
    repositoryService.createDeployment()
      .name("Simple Process")
      .addClasspathResource("bpmn/example-process.bpmn")
      .deploy()

    assertThat(repositoryService.createProcessDefinitionQuery().active().count()).isEqualTo(1)
  }
}
