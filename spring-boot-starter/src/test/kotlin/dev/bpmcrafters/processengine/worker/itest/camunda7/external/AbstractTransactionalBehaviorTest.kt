package dev.bpmcrafters.processengine.worker.itest.camunda7.external

import dev.bpmcrafters.processengine.worker.TestHelper
import dev.bpmcrafters.processengine.worker.TestHelper.Camunda7RunTestContainer
import dev.bpmcrafters.processengine.worker.itest.camunda7.external.application.MyEntity
import dev.bpmcrafters.processengine.worker.itest.camunda7.external.application.MyEntityService
import dev.bpmcrafters.processengine.worker.itest.camunda7.external.application.TestApplication
import dev.bpmcrafters.processengineapi.deploy.DeployBundleCommand
import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.deploy.NamedResource
import dev.bpmcrafters.processengineapi.process.StartProcessApi
import dev.bpmcrafters.processengineapi.process.StartProcessByDefinitionCmd
import org.assertj.core.api.Assertions.assertThat
import org.camunda.community.rest.client.api.ExternalTaskApiClient
import org.camunda.community.rest.client.api.HistoryApiClient
import org.camunda.community.rest.client.api.ProcessInstanceApiClient
import org.camunda.community.rest.client.model.ExternalTaskDto
import org.camunda.community.rest.client.model.ExternalTaskQueryDto
import org.camunda.community.rest.client.model.HistoricActivityInstanceDto
import org.camunda.community.rest.client.model.HistoricActivityInstanceQueryDto
import org.junit.jupiter.api.AfterEach
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
    val camundaContainer = Camunda7RunTestContainer("run-7.24.0")

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
  private lateinit var externalTaskApiClient: ExternalTaskApiClient
  @Autowired
  private lateinit var historyApiClient: HistoryApiClient
  @Autowired
  private lateinit var processInstanceApiClient: ProcessInstanceApiClient
  @Autowired
  private lateinit var myEntityService: MyEntityService
  @Autowired
  private lateinit var camundaTaskClient: org.camunda.bpm.client.ExternalTaskClient

  @BeforeEach
  fun setUp() {
    camundaTaskClient.start()
    deploymentApi.deploy(
      DeployBundleCommand(
        listOf(NamedResource.fromClasspath("bpmn/example-process.bpmn"))
      )
    ).get().let { deployment ->
      assertThat(deployment).isNotNull
    }
  }

  @AfterEach
  fun tearDown() {
    camundaTaskClient.stop()
  }

  protected fun processInstanceIsRunning(processInstanceId: String): Boolean {
    return processInstanceApiClient.getProcessInstancesCount(
      processInstanceId,
      null, null, // business key
      null, // case instance
      null, null, null, null, // process definition
      null, // deployment
      null, null, null, null, // super/sub process and case
      true, // active
      false, // suspended
      false, // with incident
      null, null,null, null, // incident
      null, null, null, // tenant
      null, // activity id in
      null, null, // root process instance
      true, // leaf
      null, null, null // variables
    )?.body?.count == 1L
  }

  protected fun getExternalTasks(processInstanceId: String): List<ExternalTaskDto> {
    return externalTaskApiClient
      .queryExternalTasks(0, Int.MAX_VALUE, ExternalTaskQueryDto().apply { this.processInstanceId = processInstanceId })
      .body as List<ExternalTaskDto>
  }

  protected fun getHistoricActivityInstances(processInstanceId: String, activityId: String): List<HistoricActivityInstanceDto> {
    return historyApiClient.queryHistoricActivityInstances(
      0,
      Int.MAX_VALUE,
      HistoricActivityInstanceQueryDto().apply {
        this.processInstanceId = processInstanceId
        this.activityId = activityId
      })
      .body as List<HistoricActivityInstanceDto>
  }

  protected fun startProcess(
    name: String,
    verified: Boolean,
    simulateRandomTechnicalError: Boolean = false,
    apiCallShouldFail: Boolean = false
  ): String {
    return startProcessApi.startProcess(
      StartProcessByDefinitionCmd(
        definitionKey = "example_process",
        mapOf(
          "name" to name,
          "verified" to verified,
          "simulateRandomTechnicalError" to simulateRandomTechnicalError,
          "apiCallShouldFail" to apiCallShouldFail
        )
      )
    ).get()
      .instanceId
  }

  protected fun entityExistsForName(name: String): Boolean {
    return findEntityByName(name) != null
  }

  protected fun findEntityByName(name: String): MyEntity? {
    return myEntityService.findByName(name)
  }
}
