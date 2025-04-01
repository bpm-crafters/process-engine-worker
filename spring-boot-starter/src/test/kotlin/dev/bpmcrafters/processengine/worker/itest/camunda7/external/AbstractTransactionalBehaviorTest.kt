package dev.bpmcrafters.processengine.worker.itest.camunda7.external

import dev.bpmcrafters.processengine.worker.TestHelper
import dev.bpmcrafters.processengine.worker.TestHelper.Camunda7RunTestContainer
import dev.bpmcrafters.processengine.worker.itest.camunda7.external.application.MyEntityService
import dev.bpmcrafters.processengine.worker.itest.camunda7.external.application.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.camunda.bpm.engine.RepositoryService
import org.camunda.bpm.engine.RuntimeService
import org.camunda.bpm.engine.runtime.ProcessInstance
import org.junit.jupiter.api.BeforeEach
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
abstract class AbstractTransactionalBehaviorTest {
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
      registry.add("camunda.bpm.client.base-url") { "http://localhost:${camundaContainer.firstMappedPort}/engine-rest/" }
      registry.add("feign.client.config.default.url") { "http://localhost:${camundaContainer.firstMappedPort}/engine-rest/" }
    }
  }

  @Autowired
  protected lateinit var repositoryService: RepositoryService

  @Autowired
  protected lateinit var runtimeService: RuntimeService

  @Autowired
  protected lateinit var myEntityService: MyEntityService

  @BeforeEach
  fun setUp() {
    repositoryService.createDeployment()
      .name("Example Process")
      .addClasspathResource("bpmn/example-process.bpmn")
      .deploy()

    assertThat(repositoryService.createProcessDefinitionQuery().active().latestVersion().count()).isEqualTo(1)
  }

  protected fun processInstanceIsRunning(processInstanceId: String): Boolean {
    return runtimeService.createProcessInstanceQuery()
      .active()
      .processInstanceId(processInstanceId)
      .count() > 0
  }

  protected fun startProcess(name: String, verified: Boolean, apiCallShouldFail: Boolean = false): ProcessInstance {
    return runtimeService.startProcessInstanceByKey(
      "example_process", mapOf(
        "name" to name,
        "verified" to verified,
        "apiCallShouldFail" to apiCallShouldFail
      )
    )
  }

  protected fun entityExistsForName(name: String): Boolean {
    return myEntityService.findByName(name) != null
  }
}
