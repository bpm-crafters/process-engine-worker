package dev.bpmcrafters.processengine.worker

import dev.bpmcrafters.processengine.worker.TestHelper.Camunda7RunTestContainer
import org.camunda.bpm.engine.RepositoryService
import org.camunda.community.rest.EnableCamundaRestClient
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Testcontainers
@ActiveProfiles("itest")
class TransactionalBehaviorITest {

  @Configuration
  @EnableCamundaRestClient
  class Config {}

  companion object {

  }

  @Container
  val camundaContainer = Camunda7RunTestContainer("run-7.22.0")

  @Container
  val postgresContainer = TestHelper.postgresContainer()

  @Autowired
  private lateinit var repositoryService: RepositoryService

  @Test
  fun name() {
    repositoryService.createDeployment()
      .name("Simple Process")
      .addClasspathResource("bpmn/example-process.bpmn")
      .deploy()
  }
}
