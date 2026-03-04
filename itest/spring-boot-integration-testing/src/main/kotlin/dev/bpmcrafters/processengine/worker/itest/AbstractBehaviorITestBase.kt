package dev.bpmcrafters.processengine.worker.itest

import dev.bpmcrafters.processengineapi.deploy.DeploymentApi
import dev.bpmcrafters.processengineapi.process.StartProcessApi
import org.camunda.bpm.client.ExternalTaskClient
import org.camunda.community.rest.client.api.ExternalTaskApiClient
import org.camunda.community.rest.client.api.HistoryApiClient
import org.camunda.community.rest.client.api.IncidentApiClient
import org.camunda.community.rest.client.api.ProcessInstanceApiClient
import org.camunda.community.rest.client.model.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@ActiveProfiles("itest")
abstract class AbstractBehaviorITestBase {
  companion object {

    @Container
    @JvmStatic
    val camundaContainer = TestHelper.Camunda7RunTestContainer("run-7.24.0")

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
  protected lateinit var deploymentApi: DeploymentApi

  @Autowired
  protected lateinit var startProcessApi: StartProcessApi

  @Autowired
  protected lateinit var externalTaskApiClient: ExternalTaskApiClient

  @Autowired
  protected lateinit var incidentApiClient: IncidentApiClient

  @Autowired
  protected lateinit var historyApiClient: HistoryApiClient

  @Autowired
  protected lateinit var processInstanceApiClient: ProcessInstanceApiClient

  @Autowired
  protected lateinit var camundaTaskClient: ExternalTaskClient

  @BeforeEach
  fun setUp() {
    camundaTaskClient.start()
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
      null, null, null, null, // incident
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

  protected fun unlockExternalTask(taskId: String) {
    externalTaskApiClient.unlock(taskId)
  }

  protected fun getIncidents(processInstanceId: String) = incidentApiClient.getIncidents(
    null,
    null,
    null,
    null,
    null,
    null,
    processInstanceId,
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    null
  ).body as List<IncidentDto>

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
}
