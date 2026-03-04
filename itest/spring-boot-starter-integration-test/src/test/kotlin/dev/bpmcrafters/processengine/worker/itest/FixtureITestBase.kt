package dev.bpmcrafters.processengine.worker.itest

import dev.bpmcrafters.processengine.worker.fixture.MyEntity
import dev.bpmcrafters.processengine.worker.fixture.MyEntityService
import dev.bpmcrafters.processengine.worker.fixture.TestApplication
import dev.bpmcrafters.processengineapi.deploy.DeployBundleCommand
import dev.bpmcrafters.processengineapi.deploy.NamedResource
import dev.bpmcrafters.processengineapi.process.StartProcessByDefinitionCmd
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext

@SpringBootTest(classes = [TestApplication::class])
@DirtiesContext
abstract class FixtureITestBase : AbstractBehaviorITestBase() {

  @Autowired
  private lateinit var myEntityService: MyEntityService

  fun entityExistsForName(name: String): Boolean {
    return findEntityByName(name) != null
  }

  fun findEntityByName(name: String): MyEntity? {
    return myEntityService.findByName(name)
  }

  @BeforeEach
  fun deploy() {
    deploymentApi.deploy(
      DeployBundleCommand(
        listOf(NamedResource.Companion.fromClasspath("bpmn/example-process.bpmn"))
      )
    ).get().let { deployment ->
      Assertions.assertThat(deployment).isNotNull
    }
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


}
