package dev.bpmcrafters.processengine.worker

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName



object TestHelper {

  class Camunda7RunTestContainer(tag: String) : GenericContainer<Camunda7RunTestContainer>("camunda/camunda-bpm-platform:$tag") {

    init {
      withCommand("./camunda.sh", "--rest")
      withEnv("CAMUNDA_BPM_DEFAULT-SERIALIZATION-FORMAT", "application/json")
      withExposedPorts(8080)
      addFixedExposedPort(38080, 8080)
      waitingFor(
        Wait
          .forHttp("/engine-rest/engine/")
          .forPort(8080)
      )
    }
  }

  fun postgresContainer() = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    .withDatabaseName("integration-test")
    .withUsername("integration-user")
    .withPassword("5up3R53Cr3T")

}
