package dev.bpmcrafters.processengine.worker.itest.camunda7.external

import dev.bpmcrafters.processengine.worker.TestHelper
import dev.bpmcrafters.processengine.worker.TestHelper.Camunda7RunTestContainer
import dev.bpmcrafters.processengine.worker.itest.camunda7.external.application.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.camunda.bpm.engine.ProcessEngineConfiguration
import org.camunda.bpm.engine.RepositoryService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(classes = [TestApplication::class])
@Testcontainers
@ActiveProfiles("itest")
class TransactionalBehaviorITest {

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
