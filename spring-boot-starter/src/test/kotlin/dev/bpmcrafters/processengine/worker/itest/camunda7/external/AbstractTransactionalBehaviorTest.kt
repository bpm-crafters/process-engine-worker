package dev.bpmcrafters.processengine.worker.itest.camunda7.external

import dev.bpmcrafters.processengine.worker.TestHelper
import dev.bpmcrafters.processengine.worker.TestHelper.Camunda7RunTestContainer
import dev.bpmcrafters.processengine.worker.itest.camunda7.external.application.MyEntityService
import dev.bpmcrafters.processengine.worker.itest.camunda7.external.application.TestApplication
import dev.bpmcrafters.processengineapi.deploy.DeployBundleCommand
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.deploy.NamedResource
import dev.bpmcrafters.processengineapi.process.StartProcessApi
import dev.bpmcrafters.processengineapi.process.StartProcessByDefinitionCmd
import org.assertj.core.api.Assertions.assertThat
import org.camunda.community.rest.client.api.DeploymentApiClient
import org.camunda.community.rest.client.api.ProcessDefinitionApi
import org.camunda.community.rest.client.api.ProcessDefinitionApiClient
import org.camunda.community.rest.client.api.ProcessInstanceApiClient
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatusCode
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
    val camundaContainer = Camunda7RunTestContainer("run-7.23.0")

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
  private lateinit var deploymentApi: DeploymentApi
  @Autowired
  private lateinit var startProcessApi: StartProcessApi

  @Autowired
  private lateinit var processDefinitionApiClient: ProcessDefinitionApiClient
  @Autowired
  private lateinit var processInstanceApiClient: ProcessInstanceApiClient
  @Autowired
  private lateinit var myEntityService: MyEntityService

  @BeforeEach
  fun setUp() {
    deploymentApi.deploy(
      DeployBundleCommand(
        listOf(NamedResource.fromClasspath("bpmn/example-process.bpmn"))
      )
    ).get().let { deployment ->
      assertThat(deployment).isNotNull
    }
  }

  protected fun processInstanceIsRunning(processInstanceId: String): Boolean {
    return processInstanceApiClient.getProcessInstancesCount(
      processInstanceId,
      null, null,
      null,
      null, null, null, null,
      null,
      null, null, null, null,
      true,
      false,
      false,
      null, null,null, null,
      null, null, null,
      null,
      null,
      true,
      null, null, null
    )?.body?.count == 1L
  }

  protected fun startProcess(name: String, verified: Boolean, apiCallShouldFail: Boolean = false): String {
    return startProcessApi.startProcess(
      StartProcessByDefinitionCmd(
        definitionKey = "example_process",
        mapOf(
          "name" to name,
          "verified" to verified,
          "apiCallShouldFail" to apiCallShouldFail
        )
      )
    ).get()
      .instanceId
  }

  protected fun entityExistsForName(name: String): Boolean {
    return myEntityService.findByName(name) != null
  }
}
